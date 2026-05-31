package com.aicodehub.service.tool.builtin;

import com.aicodehub.common.UserContext;
import com.aicodehub.service.tool.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FeedbackTool implements ToolDefinition {

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PREFIX = "feedback:";

    public FeedbackTool(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override public String name() { return "save_feedback"; }

    @Override
    public String description() {
        return "保存用户对回答质量的评价。当用户表达满意/不满或对答案打分时使用。评分1-5，可附带具体意见。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> rating = new LinkedHashMap<>();
        rating.put("type", "integer");
        rating.put("description", "评分 1-5，5为非常满意");
        props.put("rating", rating);
        Map<String, Object> comment = new LinkedHashMap<>();
        comment.put("type", "string");
        comment.put("description", "具体反馈意见或不满原因");
        props.put("comment", comment);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "对应的用户问题");
        props.put("query", query);
        params.put("properties", props);
        params.put("required", List.of("rating"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        int rating = args.get("rating") instanceof Integer r ? r : Integer.parseInt(args.get("rating").toString());
        String comment = (String) args.getOrDefault("comment", "");
        String query = (String) args.getOrDefault("query", "");
        Long userId = UserContext.getUserId();

        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", userId);
            entry.put("rating", rating);
            entry.put("comment", comment);
            entry.put("query", query);
            entry.put("timestamp", Instant.now().toString());

            String key = PREFIX + userId + ":" + Instant.now().toEpochMilli();
            redis.opsForValue().set(key, json.writeValueAsString(entry), Duration.ofDays(90));
            log.info("Feedback saved: userId={}, rating={}", userId, rating);
            return "反馈已记录，评分: " + rating + "/5，感谢您的评价！";
        } catch (Exception e) {
            log.error("Feedback save failed: {}", e.getMessage());
            return "反馈保存失败: " + e.getMessage();
        }
    }
}
