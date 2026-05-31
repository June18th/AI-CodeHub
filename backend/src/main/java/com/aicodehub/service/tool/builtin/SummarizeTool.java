package com.aicodehub.service.tool.builtin;

import com.aicodehub.config.AiModelProperties;
import com.aicodehub.service.tool.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class SummarizeTool implements ToolDefinition {

    private final AiModelProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public SummarizeTool(AiModelProperties properties) {
        this.properties = properties;
    }

    @Override public String name() { return "summarize"; }

    @Override
    public String description() {
        return "对多篇文档内容生成结构化摘要。先调用 rag_search 获取文档内容后，再调用此工具进行归纳总结。输入需要摘要的文本内容和摘要要求。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "string");
        content.put("description", "需要摘要的文档内容文本");
        props.put("content", content);
        Map<String, Object> instruction = new LinkedHashMap<>();
        instruction.put("type", "string");
        instruction.put("description", "摘要要求，例如'用三句话总结'或'列出关键要点'");
        props.put("instruction", instruction);
        params.put("properties", props);
        params.put("required", List.of("content"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String content = (String) args.get("content");
        String instruction = (String) args.getOrDefault("instruction", "请用简洁的中文总结以下内容要点");

        AiModelProperties.ModelConfig config = properties.getConfig("deepseek");
        if (config == null) config = properties.getConfig("qwen");
        if (config == null) return "摘要生成失败: 无可用模型配置";

        try {
            List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "你是一个专业的文档摘要助手。" + instruction),
                Map.of("role", "user", "content", content.substring(0, Math.min(content.length(), 8000)))
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getModel());
            body.put("messages", messages);
            body.put("max_tokens", 1024);
            body.put("temperature", 0.3);

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var root = json.readTree(resp.body());
                var choices = root.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    var msg = choices.get(0).get("message").get("content");
                    return msg != null ? msg.asText() : "摘要生成返回为空";
                }
            }
            return "摘要生成失败: HTTP " + resp.statusCode();
        } catch (Exception e) {
            log.error("Summarize tool failed: {}", e.getMessage());
            return "摘要生成异常: " + e.getMessage();
        }
    }
}
