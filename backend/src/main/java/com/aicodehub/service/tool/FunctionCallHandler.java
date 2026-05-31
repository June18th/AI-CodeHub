package com.aicodehub.service.tool;

import com.aicodehub.config.AiModelProperties;
import com.aicodehub.service.agent.AgentBudget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
public class FunctionCallHandler {

    private static final int MAX_ITERATIONS = 5;
    private static final int TOKEN_BUDGET = 16000;

    private final ToolRegistry toolRegistry;
    private final AiModelProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public FunctionCallHandler(ToolRegistry toolRegistry, AiModelProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public void handleWithTools(String modelType, List<Map<String, String>> messages,
                                 Consumer<String> onChunk, Consumer<String> onToolCall,
                                 Runnable onDone, Consumer<String> onError) {
        AiModelProperties.ModelConfig config = properties.getConfig(modelType);
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            onError.accept("模型未配置 API Key");
            return;
        }

        new Thread(() -> {
            try {
                List<Map<String, Object>> history = new ArrayList<>();
                for (Map<String, String> m : messages) {
                    history.add(Map.of("role", m.get("role"), "content", m.get("content")));
                }

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                AgentBudget budget = new AgentBudget(MAX_ITERATIONS, TOKEN_BUDGET);

                while (true) {
                    if (budget.isExhausted()) {
                        emitToolEvent(onChunk, "budget_exceeded",
                            Map.of("iterations", budget.getIterationsUsed(),
                                   "tokens", budget.getTokensUsed(),
                                   "maxIterations", budget.getMaxIterations()));
                        onChunk.accept("\n\n> ⚠️ **预算耗尽** (" + budget.status()
                            + ")，强制输出最终结果\n\n");

                        String forced = forceTextResponse(client, config, history);
                        if (forced != null) {
                            onChunk.accept(forced);
                        }
                        break;
                    }

                    budget.incrementIteration();
                    boolean forceText = budget.isExhausted();

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("model", config.getModel());
                    body.put("messages", history);
                    if (!forceText) {
                        body.put("tools", toolRegistry.toOpenAiFormat());
                        body.put("tool_choice", "auto");
                    }

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        onError.accept("API 错误: HTTP " + response.statusCode());
                        onDone.run();
                        return;
                    }

                    JsonNode root = json.readTree(response.body());
                    JsonNode usage = root.get("usage");
                    if (usage != null) {
                        int tokens = 0;
                        if (usage.has("total_tokens")) tokens = usage.get("total_tokens").asInt();
                        else if (usage.has("prompt_tokens")) tokens = usage.get("prompt_tokens").asInt();
                        budget.spendTokens(tokens);
                    }

                    JsonNode choices = root.get("choices");
                    if (choices == null || choices.isEmpty()) { onDone.run(); return; }

                    JsonNode message = choices.get(0).get("message");
                    JsonNode toolCalls = (message != null) ? message.get("tool_calls") : null;

                    if (toolCalls != null && !toolCalls.isEmpty() && !forceText) {
                        // ---- Tool calls detected — execute and continue loop ----
                        List<Map<String, Object>> tcList = new ArrayList<>();
                        for (JsonNode tc : toolCalls) {
                            Map<String, Object> tcMap = new LinkedHashMap<>();
                            tcMap.put("id", tc.get("id").asText());
                            tcMap.put("type", "function");
                            JsonNode func = tc.get("function");
                            tcMap.put("function", Map.of(
                                "name", func.get("name").asText(),
                                "arguments", func.get("arguments").asText()));
                            tcList.add(tcMap);
                        }

                        String assistantContent = (message.has("content") && !message.get("content").isNull())
                            ? message.get("content").asText() : "";

                        Map<String, Object> assistantMsg = new LinkedHashMap<>();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", assistantContent);
                        assistantMsg.put("tool_calls", tcList);
                        history.add(assistantMsg);

                        for (JsonNode tc : toolCalls) {
                            String toolName = tc.get("function").get("name").asText();
                            String toolArgs = tc.get("function").get("arguments").asText();
                            String toolId = tc.get("id").asText();

                            emitToolEvent(onChunk, "tool_call",
                                Map.of("tool", toolName, "status", "executing"));
                            onToolCall.accept(toolName);

                            String result;
                            try {
                                ToolDefinition tool = toolRegistry.get(toolName);
                                if (tool != null) {
                                    Map<String, Object> argsMap = json.readValue(toolArgs, Map.class);
                                    result = tool.execute(argsMap);
                                    emitToolEvent(onChunk, "tool_result",
                                        Map.of("tool", toolName, "status", "completed"));
                                } else {
                                    result = "错误: 未知工具 '" + toolName + "'";
                                    emitToolEvent(onChunk, "tool_result",
                                        Map.of("tool", toolName, "status", "error",
                                               "error", "unknown_tool"));
                                }
                            } catch (Exception e) {
                                log.error("Tool {} execution failed: {}", toolName, e.getMessage());
                                result = "工具执行异常: " + e.getMessage();
                                emitToolEvent(onChunk, "tool_result",
                                    Map.of("tool", toolName, "status", "error",
                                           "error", e.getMessage()));
                            }

                            onChunk.accept("\n📋 **" + toolName + "** 结果:\n```\n" + result + "\n```\n");

                            Map<String, Object> toolMsg = new LinkedHashMap<>();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", toolId);
                            toolMsg.put("content", result);
                            history.add(toolMsg);
                        }
                        // Continue while(true) — model re-evaluates with tool results
                    } else {
                        // ---- No tool calls — stream final answer and exit ----
                        Map<String, Object> body2 = new LinkedHashMap<>();
                        body2.put("model", config.getModel());
                        body2.put("messages", history);
                        body2.put("stream", true);

                        HttpRequest req2 = HttpRequest.newBuilder()
                            .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                            .header("Authorization", "Bearer " + config.getApiKey())
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(120))
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body2)))
                            .build();

                        HttpResponse<java.io.InputStream> streamResp =
                            client.send(req2, HttpResponse.BodyHandlers.ofInputStream());

                        int outTokens = 0;
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(streamResp.body()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if ("[DONE]".equals(data)) break;
                                    try {
                                        JsonNode node = json.readTree(data);
                                        JsonNode u = node.get("usage");
                                        if (u != null && u.has("completion_tokens"))
                                            outTokens = u.get("completion_tokens").asInt();
                                        JsonNode ch = node.get("choices");
                                        if (ch != null && !ch.isEmpty()) {
                                            JsonNode delta = ch.get(0).get("delta");
                                            if (delta != null) {
                                                JsonNode cnt = delta.get("content");
                                                if (cnt != null && !cnt.asText().isEmpty())
                                                    onChunk.accept(cnt.asText());
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                        budget.spendTokens(outTokens);
                        String tokenInfo = buildTokenInfo(root, outTokens);
                        if (!tokenInfo.isEmpty()) onChunk.accept(tokenInfo);
                        break;
                    }
                }
                onDone.run();
            } catch (Exception e) {
                log.error("Agent error for {}: {}", modelType, e.getMessage());
                onError.accept("Agent 调用失败: " + e.getMessage());
                onDone.run();
            }
        }).start();
    }

    /** Force a text-only response when budget is exhausted */
    private String forceTextResponse(HttpClient client, AiModelProperties.ModelConfig config,
                                      List<Map<String, Object>> history) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getModel());
            body.put("messages", history);
            body.put("stream", false);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = json.readTree(resp.body());
                JsonNode choices = root.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    JsonNode content = choices.get(0).get("message").get("content");
                    if (content != null) return content.asText();
                }
            }
        } catch (Exception e) {
            log.error("Force text response failed: {}", e.getMessage());
        }
        return "抱歉，处理超时，请简化问题后重试。";
    }

    private void emitToolEvent(Consumer<String> onChunk, String type, Map<String, Object> payload) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", type);
            event.putAll(payload);
            onChunk.accept("[EVENT]" + json.writeValueAsString(event));
        } catch (Exception ignored) {}
    }

    private String buildTokenInfo(JsonNode root, int streamTokens) {
        if (root == null) return "";
        JsonNode usage = root.get("usage");
        if (usage == null) return "";
        int in = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
        int out = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : streamTokens;
        return "[TOKEN]input=" + in + ",output=" + out;
    }
}
