package com.aicodehub.service.tool.builtin;

import com.aicodehub.service.tool.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WebSearchTool implements ToolDefinition {

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebSearchTool(@Value("${serpapi.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() { return "web_search"; }

    @Override
    public String description() {
        return "联网搜索，返回标题、链接和摘要。适合查询实时信息、最新新闻、事实核查等。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "搜索关键词");
        props.put("query", query);
        Map<String, Object> num = new LinkedHashMap<>();
        num.put("type", "integer");
        num.put("description", "返回结果数量，默认5，最大10");
        props.put("num", num);
        params.put("properties", props);
        params.put("required", List.of("query"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        if (apiKey == null || apiKey.isBlank()) {
            return "SerpAPI Key 未配置，请在 .env 中设置 SERPAPI_API_KEY。免费获取: https://serpapi.com";
        }
        String query = (String) args.get("query");
        int num = Math.min(args.get("num") instanceof Integer i ? i : 5, 10);
        try {
            String url = "https://serpapi.com/search?api_key=" + apiKey
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&num=" + num + "&engine=google";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET().build();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "搜索失败: HTTP " + response.statusCode();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode results = root.path("organic_results");
            if (!results.isArray() || results.isEmpty()) {
                return "未找到相关结果";
            }

            StringBuilder sb = new StringBuilder("🔍 搜索结果: \"" + query + "\"\n\n");
            for (int i = 0; i < results.size(); i++) {
                JsonNode r = results.get(i);
                sb.append(i + 1).append(". **").append(r.path("title").asText("无标题")).append("**\n");
                sb.append("   ").append(r.path("snippet").asText("")).append("\n");
                sb.append("   🔗 ").append(r.path("link").asText("")).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("Web search failed for {}", query, e);
            return "搜索异常: " + e.getMessage();
        }
    }
}
