package com.aicodehub.service.tool.builtin;

import com.aicodehub.service.tool.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WeatherTool implements ToolDefinition {

    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherTool(@Value("${amap.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() { return "get_weather"; }

    @Override
    public String description() { return "查询指定城市的实时天气信息，包括温度、湿度、风力、天气状况等。输入城市名称如'北京'、'上海'。"; }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> city = new LinkedHashMap<>();
        city.put("type", "string");
        city.put("description", "城市名称，如北京、上海、深圳");
        props.put("city", city);
        params.put("properties", props);
        params.put("required", List.of("city"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        if (apiKey == null || apiKey.isBlank()) {
            return "高德地图 API Key 未配置，请在 .env 中设置 AMAP_API_KEY。获取地址: https://lbs.amap.com";
        }
        String city = (String) args.get("city");
        try {
            String url = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + apiKey
                + "&city=" + java.net.URLEncoder.encode(city, "UTF-8")
                + "&extensions=base&output=JSON";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "天气查询失败: HTTP " + response.statusCode();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("status") && !"1".equals(root.get("status").asText())) {
                return "天气查询失败: " + root.path("info").asText("未知错误");
            }

            JsonNode lives = root.path("lives");
            if (!lives.isArray() || lives.isEmpty()) {
                return "未找到城市 '" + city + "' 的天气信息";
            }

            JsonNode live = lives.get(0);
            return String.format(
                "%s 当前天气:\n🌡 温度: %s°C (体感 %s°C)\n💧 湿度: %s%%\n🌬 风向: %s 风力: %s级\n☁ 天气: %s\n📊 数据来源: 高德地图",
                live.path("city").asText(),
                live.path("temperature").asText(),
                live.path("temperature_float").asText(),
                live.path("humidity").asText(),
                live.path("winddirection").asText(),
                live.path("windpower").asText(),
                live.path("weather").asText()
            );
        } catch (Exception e) {
            log.error("Weather query failed for {}", city, e);
            return "天气查询异常: " + e.getMessage();
        }
    }
}
