package com.aicodehub.service.ws;

import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.entity.Conversation;
import com.aicodehub.entity.Message;
import com.aicodehub.service.AiApiClient;
import com.aicodehub.service.AuditService;
import com.aicodehub.service.ConversationService;
import com.aicodehub.service.MemoryService;
import com.aicodehub.service.tool.FunctionCallHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final AiApiClient apiClient;
    private final ConversationService conversationService;
    private final MemoryService memoryService;
    private final AuditService auditService;
    private final FunctionCallHandler functionCallHandler;
    private final com.aicodehub.service.agent.CrewOrchestrator crewOrchestrator;
    private final com.aicodehub.service.agent.AgentWorker agentWorker;
    private final ObjectMapper mapper = new ObjectMapper();

    private String breakdownJson(List<Map<String, String>> messages, int input, int output) {
        int sys = 0, user = 0, mem = 0;
        for (var m : messages) {
            String r = m.get("role");
            int t = m.get("content") != null ? m.get("content").length() / 3 : 0;
            if ("system".equals(r)) { if (m.get("content").contains("以下是历史关键信息")) mem = t; else sys = t; }
            else if ("user".equals(r)) user += t;
        }
        int other = Math.max(0, input - sys - user - mem);
        return mapper.createObjectNode()
            .put("user_prompt", user).put("system_prompt", sys)
            .put("memory_context", mem).put("history", other)
            .put("output", output).toString();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        log.info("WebSocket connected: session={}, userId={}", session.getId(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            var json = mapper.readTree(message.getPayload());
            String type = json.path("type").asText("");
            Long userId = (Long) session.getAttributes().get("userId");
            String modelType = json.path("modelType").asText("deepseek");

            switch (type) {
                case "ping" -> session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                case "chat" -> handleChat(session, userId, modelType, json);
                case "agent" -> handleAgent(session, userId, modelType, json);
                case "crew" -> handleCrew(session, userId, modelType, json);
                case "resume" -> handleResume(session, userId, json);
                default -> session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Unknown type: " + type + "\"}"));
            }
        } catch (Exception e) {
            log.error("WebSocket message error", e);
            send(session, "error", Map.of("message", e.getMessage()));
        }
    }

    private void handleChat(WebSocketSession session, Long userId, String modelType, JsonNode json) throws Exception {
        String prompt = json.path("prompt").asText();
        long conversationId = json.path("conversationId").asLong(-1);

        Conversation conv = null;
        if (conversationId > 0) {
            conv = conversationService.getById(conversationId);
        }
        if (conv == null && userId != null) {
            conv = conversationService.create(userId, modelType, prompt);
        }
        if (conv == null) {
            // Guest mode
            final long start = System.nanoTime();
            var wrapper = new WebSocketWrapper(session);
            apiClient.streamCall(modelType, prompt,
                wrapper::send, () -> {
                    auditService.log(userId, null, modelType, "/ws/chat", wrapper.in, wrapper.out,
                        (int)((System.nanoTime() - start) / 1_000_000), "success", null);
                    send(session, "done", Map.of());
                },
                err -> send(session, "error", Map.of("message", err)));
            return;
        }

        final Long cid = conv.getId();
        conversationService.saveMessage(cid, "user", prompt);
        List<Map<String, String>> messages = memoryService.buildContext(userId, cid, prompt, modelType);

        final long start = System.nanoTime();
        var wrapper = new WebSocketWrapper(session);
        apiClient.streamCall(modelType, messages, wrapper::send, () -> {
            String response = wrapper.sb.toString();
            if (!response.isEmpty()) {
                conversationService.saveMessage(cid, "assistant", response,
                    wrapper.in > 0 ? wrapper.in : null, wrapper.out > 0 ? wrapper.out : null);
                conversationService.updateTitle(cid, prompt);
            }
            send(session, "token", Map.of("input", wrapper.in, "output", wrapper.out));
            auditService.log(userId, null, modelType, "/ws/chat", wrapper.in, wrapper.out,
                (int)((System.nanoTime() - start) / 1_000_000), "success", null,
                breakdownJson(messages, wrapper.in, wrapper.out));
            if (userId != null) memoryService.maybeCompress(userId, cid);
            send(session, "done", Map.of());
        }, err -> {
            send(session, "error", Map.of("message", err));
            auditService.log(userId, null, modelType, "/ws/chat", 0, 0,
                (int)((System.nanoTime() - start) / 1_000_000), "error", err);
        });
    }

    private void handleAgent(WebSocketSession session, Long userId, String modelType, JsonNode json) throws Exception {
        String prompt = json.path("prompt").asText();
        long conversationId = json.path("conversationId").asLong(-1);

        final List<Map<String, String>> messages = new ArrayList<>();
        if (conversationId > 0) {
            conversationService.saveMessage(conversationId, "user", prompt);
            messages.addAll(memoryService.buildContext(userId, conversationId, prompt, modelType));
        } else {
            messages.add(Map.of("role", "user", "content", prompt));
        }

        final long start = System.nanoTime();
        var wrapper = new WebSocketWrapper(session);
        functionCallHandler.handleWithTools(modelType, messages,
            wrapper::send,
            toolName -> send(session, "tool_call", Map.of("tool", toolName)),
            () -> {
                auditService.log(userId, null, modelType, "/ws/agent", wrapper.in, wrapper.out,
                    (int)((System.nanoTime() - start) / 1_000_000), "success", null,
                    breakdownJson(messages, wrapper.in, wrapper.out));
                if (conversationId > 0 && !wrapper.sb.isEmpty()) {
                    conversationService.saveMessage(conversationId, "assistant", wrapper.sb.toString(),
                        wrapper.in > 0 ? wrapper.in : null, wrapper.out > 0 ? wrapper.out : null);
                    conversationService.updateTitle(conversationId, prompt);
                    if (userId != null) memoryService.maybeCompress(userId, conversationId);
                }
                send(session, "token", Map.of("input", wrapper.in, "output", wrapper.out));
                send(session, "done", Map.of());
            },
            err -> {
                send(session, "error", Map.of("message", err));
                auditService.log(userId, null, modelType, "/ws/agent", 0, 0,
                    (int)((System.nanoTime() - start) / 1_000_000), "error", err);
            });
    }

    private void handleCrew(WebSocketSession session, Long userId, String modelType, JsonNode json) {
        String prompt = json.path("prompt").asText();

        // Phase 1: Architect splits into subtasks
        send(session, "crew_status", Map.of("phase", "plan", "message", "架构师正在分析任务..."));
        agentWorker.execute("架构师",
            "你是任务分析专家，将用户请求拆分为2-3个独立的子任务。每个子任务一行，只输出子任务内容。",
            modelType, List.of(), prompt)
            .thenAccept(splitOutput -> {
                String[] tasks = java.util.Arrays.stream(splitOutput.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.replaceAll("^[0-9]+[\\.\\)、]\\s*", ""))
                    .filter(s -> s.length() > 2)
                    .toArray(String[]::new);
                send(session, "crew_status", Map.of("phase", "execute", "message", "拆分为 " + tasks.length + " 个子任务，并行执行中..."));

                // Phase 2: Developers execute subtasks in parallel (round-robin)
                int devCount = 2;
                List<CompletableFuture<String>> futures = new ArrayList<>();
                for (int i = 0; i < tasks.length; i++) {
                    final int idx = i;
                    String devPrompt = "你是专业开发者，高效准确地完成任务。用中文输出结果。";
                    send(session, "crew_status", Map.of("phase", "execute", "agent", "开发者-" + ((idx % devCount) + 1), "task", tasks[idx].trim()));
                    futures.add(agentWorker.execute("开发者-" + ((idx % devCount) + 1), devPrompt, modelType, List.of(), tasks[idx].trim()));
                }

                // Phase 3: Merge results
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .thenRun(() -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < tasks.length; i++) {
                            try { sb.append("**").append(tasks[i].trim()).append("**\n").append(futures.get(i).get()).append("\n\n"); }
                            catch (Exception e) { sb.append("**").append(tasks[i].trim()).append("**\n执行失败\n\n"); }
                        }
                        send(session, "chunk", Map.of("content", sb.toString().trim()));
                        send(session, "done", Map.of());
                    });
            });
    }

    private void handleResume(WebSocketSession session, Long userId, JsonNode json) {
        long cid = json.path("conversationId").asLong(-1);
        if (cid > 0) {
            var messages = conversationService.getMessages(cid);
            send(session, "resumed", Map.of("conversationId", cid, "messageCount", messages.size()));
        }
    }

    private void send(WebSocketSession session, String type, Map<String, Object> data) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("type", type);
            var d = mapper.valueToTree(data);
            payload.set("data", d);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.error("WebSocket send failed", e);
        }
    }

    /** Wraps WebSocket session to capture response text + token counts like SseSaveWrapper */
    private class WebSocketWrapper {
        final WebSocketSession session;
        final StringBuilder sb = new StringBuilder();
        int in, out;
        WebSocketWrapper(WebSocketSession s) { this.session = s; }
        void send(String chunk) {
            if (chunk.startsWith("[TOKEN]input=")) {
                var m = java.util.regex.Pattern.compile("input=(\\d+),output=(\\d+)").matcher(chunk);
                if (m.find()) { in = Integer.parseInt(m.group(1)); out = Integer.parseInt(m.group(2)); }
                return;
            }
            sb.append(chunk);
            try { session.sendMessage(new TextMessage("{\"type\":\"chunk\",\"content\":" + mapper.writeValueAsString(chunk) + "}")); }
            catch (Exception ignored) {}
        }
    }
}
