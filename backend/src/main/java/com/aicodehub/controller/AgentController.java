package com.aicodehub.controller;

import com.aicodehub.common.SseEmitterHelper;
import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.entity.Message;
import com.aicodehub.service.ConversationService;
import com.aicodehub.service.tool.FunctionCallHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private static final String SYSTEM_GUARD = """
        你是 AI-CodeHub 智能助手，可以使用工具完成任务。请遵守以下规则：
        1. 如果不确定该怎么做，直接询问用户，不要猜测或编造答案
        2. 每次工具调用前先思考：这个工具是否真的必要？是否有更直接的方式？
        3. 工具返回错误时，分析原因并尝试替代方案，不要重复相同的失败调用
        4. 完成用户任务后，用简洁的中文总结结果""";

    private final FunctionCallHandler functionCallHandler;
    private final ConversationService conversationService;
    private final com.aicodehub.service.AuditService auditService;
    private final com.aicodehub.common.JwtUtil jwtUtil;
    private final com.aicodehub.service.SessionService sessionService;

    @GetMapping("/chat")
    public SseEmitter agentChat(@RequestParam String prompt,
                                @RequestParam(defaultValue = "deepseek") String modelType,
                                @RequestParam(required = false) Long conversationId,
                                @RequestHeader(value = "Authorization", required = false) String auth) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(300_000L);
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                String token = auth.substring(7);
                if (jwtUtil.validate(token)) {
                    Long uid = jwtUtil.getUserId(token);
                    try { sessionService.extend(uid); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        SseSaveWrapper wrapper = new SseSaveWrapper(emitter);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_GUARD));

        if (conversationId != null) {
            conversationService.saveMessage(conversationId, "user", prompt);
            List<Message> history = conversationService.getContext(conversationId);
            for (Message m : history) {
                messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", prompt));

        final long start = System.nanoTime();
        functionCallHandler.handleWithTools(modelType, messages,
            wrapper::send,
            toolName -> wrapper.send("🔧 调用工具: " + toolName + "..."),
            () -> wrapper.complete(() -> {
                int in = wrapper.getInputTokens();
                int out = wrapper.getOutputTokens();
                auditService.log(null, null, modelType, "/api/v1/agent/chat", in, out,
                    (int)((System.nanoTime() - start) / 1_000_000), "success", null);
                if (conversationId != null) {
                    String resp = wrapper.getResponse();
                    if (!resp.isEmpty()) {
                        conversationService.saveMessage(conversationId, "assistant", resp,
                            in > 0 ? in : null, out > 0 ? out : null);
                        conversationService.updateTitle(conversationId, prompt);
                    }
                }
            }),
            errMsg -> {
                auditService.log(null, null, modelType, "/api/v1/agent/chat", 0, 0,
                    (int)((System.nanoTime() - start) / 1_000_000), "error", errMsg);
                wrapper.send(errMsg);
                wrapper.complete(() -> {});
            });

        return emitter;
    }
}
