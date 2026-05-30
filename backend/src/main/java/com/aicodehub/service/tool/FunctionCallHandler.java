package com.aicodehub.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aicodehub.config.AiModelProperties;
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

    private final ToolRegistry toolRegistry;
    private final AiModelProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FunctionCallHandler(ToolRegistry toolRegistry, AiModelProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    /**
     * Handle a chat request with tool calling support.
     * Returns the final messages array and streams the final response via onChunk.
     * If no tool is called, streams directly.
     */
    @SuppressWarnings("unchecked")
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
                List<Map<String, Object>> openAiMessages = new ArrayList<>();
                for (Map<String, String> m : messages) {
                    openAiMessages.add(Map.of("role", m.get("role"), "content", m.get("content")));
                }

                // Send with tools
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", config.getModel());
                body.put("messages", openAiMessages);
                body.put("tools", toolRegistry.toOpenAiFormat());
                body.put("tool_choice", "auto");

                String json = objectMapper.writeValueAsString(body);
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

                // First call: non-streaming to get tool_calls or answer
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    onError.accept("API 错误: HTTP " + response.statusCode());
                    onDone.run();
                    return;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.get("choices");
                if (choices == null || choices.isEmpty()) {
                    onDone.run();
                    return;
                }

                JsonNode message = choices.get(0).get("message");
                JsonNode toolCalls = message.get("tool_calls");

                if (toolCalls != null && !toolCalls.isEmpty()) {
                    // ---- Tool call detected ----
                    // Add assistant message with tool_calls
                    Map<String, Object> assistantMsg = new LinkedHashMap<>();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", message.has("content") && !message.get("content").isNull()
                        ? message.get("content").asText() : "");
                    List<Map<String, Object>> tcList = new ArrayList<>();
                    for (JsonNode tc : toolCalls) {
                        Map<String, Object> tcMap = new LinkedHashMap<>();
                        tcMap.put("id", tc.get("id").asText());
                        tcMap.put("type", "function");
                        JsonNode func = tc.get("function");
                        tcMap.put("function", Map.of("name", func.get("name").asText(),
                            "arguments", func.get("arguments").asText()));
                        tcList.add(tcMap);
                    }
                    assistantMsg.put("tool_calls", tcList);
                    openAiMessages.add(assistantMsg);

                    // Execute each tool
                    for (JsonNode tc : toolCalls) {
                        String toolName = tc.get("function").get("name").asText();
                        String toolArgs = tc.get("function").get("arguments").asText();
                        String toolId = tc.get("id").asText();

                        onToolCall.accept(toolName);
                        ToolDefinition tool = toolRegistry.get(toolName);
                        String result;
                        if (tool != null) {
                            Map<String, Object> argsMap = objectMapper.readValue(toolArgs, Map.class);
                            result = tool.execute(argsMap);
                        } else {
                            result = "未知工具: " + toolName;
                        }
                        // Also send tool result to client (in code block to avoid markdown rendering)
                        onChunk.accept("\n📋 工具结果:\n```\n" + result + "\n```\n");

                        // Add tool result
                        Map<String, Object> toolMsg = new LinkedHashMap<>();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", toolId);
                        toolMsg.put("content", result);
                        openAiMessages.add(toolMsg);
                    }

                    // Second call: streaming final response with accumulated context
                    Map<String, Object> body2 = new LinkedHashMap<>();
                    body2.put("model", config.getModel());
                    body2.put("messages", openAiMessages);
                    body2.put("stream", true);

                    String json2 = objectMapper.writeValueAsString(body2);
                    HttpRequest request2 = HttpRequest.newBuilder()
                        .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(json2))
                        .build();

                    HttpResponse<java.io.InputStream> streamResp = client.send(request2,
                        HttpResponse.BodyHandlers.ofInputStream());

                    int inputTokens = 0, outputTokens = 0;
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(streamResp.body()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) break;
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode usage = node.get("usage");
                                    if (usage != null) {
                                        if (usage.has("prompt_tokens")) inputTokens = usage.get("prompt_tokens").asInt();
                                        if (usage.has("completion_tokens")) outputTokens = usage.get("completion_tokens").asInt();
                                    }
                                    JsonNode ch = node.get("choices");
                                    if (ch != null && !ch.isEmpty()) {
                                        JsonNode delta = ch.get(0).get("delta");
                                        if (delta != null) {
                                            JsonNode content = delta.get("content");
                                            if (content != null && !content.asText().isEmpty()) {
                                                outputTokens++;
                                                onChunk.accept(content.asText());
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    if (inputTokens > 0 || outputTokens > 0) {
                        onChunk.accept("[TOKEN]input=" + inputTokens + ",output=" + outputTokens);
                    }
                } else {
                    // ---- No tool call: stream the content from this response ----
                    int inputTokens = 0, outputTokens = 0;
                    // Get usage from the non-streaming response
                    JsonNode rootUsage = root.get("usage");
                    if (rootUsage != null) {
                        if (rootUsage.has("prompt_tokens")) inputTokens = rootUsage.get("prompt_tokens").asInt();
                        if (rootUsage.has("completion_tokens")) outputTokens = rootUsage.get("completion_tokens").asInt();
                    }
                    // Also make a streaming call for consistency
                    Map<String, Object> body2 = new LinkedHashMap<>();
                    body2.put("model", config.getModel());
                    body2.put("messages", openAiMessages);
                    body2.put("stream", true);
                    String json2 = objectMapper.writeValueAsString(body2);
                    HttpRequest request2 = HttpRequest.newBuilder()
                        .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(json2))
                        .build();
                    HttpResponse<java.io.InputStream> streamResp = client.send(request2,
                        HttpResponse.BodyHandlers.ofInputStream());
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(streamResp.body()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) break;
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode usage = node.get("usage");
                                    if (usage != null) {
                                        if (usage.has("prompt_tokens")) inputTokens = usage.get("prompt_tokens").asInt();
                                        if (usage.has("completion_tokens")) outputTokens = usage.get("completion_tokens").asInt();
                                    }
                                    JsonNode ch = node.get("choices");
                                    if (ch != null && !ch.isEmpty()) {
                                        JsonNode delta = ch.get(0).get("delta");
                                        if (delta != null) {
                                            JsonNode cnt = delta.get("content");
                                            if (cnt != null && !cnt.asText().isEmpty()) {
                                                onChunk.accept(cnt.asText());
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    if (inputTokens > 0 || outputTokens > 0) {
                        onChunk.accept("[TOKEN]input=" + inputTokens + ",output=" + outputTokens);
                    }
                }
                onDone.run();
            } catch (Exception e) {
                log.error("Tool call error for {}: {}", modelType, e.getMessage());
                onError.accept("Agent 调用失败: " + e.getMessage());
                onDone.run();
            }
        }).start();
    }
}
