package com.aicodehub.service;

import com.aicodehub.config.AiModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class AiApiClient {

    private final AiModelProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile int failureCount = 0;
    private volatile long circuitOpenUntil = 0;
    private static final int FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_TIMEOUT_MS = 30_000;

    public AiApiClient(AiModelProperties properties) {
        this.properties = properties;
    }

    private boolean isCircuitOpen() {
        if (failureCount >= FAILURE_THRESHOLD) {
            if (System.currentTimeMillis() < circuitOpenUntil) return true;
            failureCount = 0;
        }
        return false;
    }

    public void streamCall(String modelType, String prompt,
                           Consumer<String> onChunk, Runnable onDone,
                           Consumer<String> onError) {
        streamCall(modelType, List.of(Map.of("role", "user", "content", prompt)),
            onChunk, onDone, onError);
    }

    public void streamCall(String modelType, java.util.List<java.util.Map<String, String>> messages,
                           Consumer<String> onChunk, Runnable onDone,
                           Consumer<String> onError) {
        AiModelProperties.ModelConfig config = properties.getConfig(modelType);
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            onError.accept("模型 [" + modelType + "] 未配置 API Key，请在 .env 中设置");
            return;
        }

        if (isCircuitOpen()) {
            onError.accept("AI 服务暂时不可用，请稍后再试（熔断保护）");
            onDone.run();
            return;
        }

        new Thread(() -> {
            try {
                var body = Map.of(
                    "model", config.getModel(),
                    "messages", messages,
                    "stream", true
                );
                String json = objectMapper.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                HttpResponse<java.io.InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    failureCount++;
                    if (failureCount >= FAILURE_THRESHOLD) {
                        circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_TIMEOUT_MS;
                        log.warn("Circuit breaker OPEN for AI model calls ({} consecutive failures)", failureCount);
                    }
                    onError.accept("API 返回错误: HTTP " + response.statusCode());
                    return;
                }
                failureCount = 0;

                int[] tokenCount = new int[2]; // [input, output]
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) break;
                            try {
                                JsonNode node = objectMapper.readTree(data);
                                JsonNode usage = node.get("usage");
                                if (usage != null) {
                                    if (usage.has("prompt_tokens"))
                                        tokenCount[0] = usage.get("prompt_tokens").asInt();
                                    if (usage.has("completion_tokens"))
                                        tokenCount[1] = usage.get("completion_tokens").asInt();
                                }
                                JsonNode choices = node.get("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null) {
                                        JsonNode content = delta.get("content");
                                        if (content != null && !content.asText().isEmpty()) {
                                            tokenCount[1]++;
                                            onChunk.accept(content.asText());
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                // Send token info
                if (tokenCount[0] > 0 || tokenCount[1] > 0) {
                    onChunk.accept("[TOKEN]input=" + tokenCount[0] + ",output=" + tokenCount[1]);
                }
                onDone.run();
            } catch (Exception e) {
                log.error("API call failed for {}: {}", modelType, e.getMessage());
                failureCount++;
                if (failureCount >= FAILURE_THRESHOLD) {
                    circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_TIMEOUT_MS;
                    log.warn("Circuit breaker OPEN for AI model calls ({} consecutive failures)", failureCount);
                }
                onError.accept("调用失败: " + e.getMessage());
                onDone.run();
            }
        }).start();
    }
}
