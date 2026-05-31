package com.aicodehub.service;

import com.aicodehub.entity.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    private static final String PREFIX = "msg_list:";
    private static final int MAX_CACHED = 20;
    private static final Duration TTL = Duration.ofHours(2);

    public List<Message> getRecent(Long conversationId) {
        String key = PREFIX + conversationId;
        List<String> items = redis.opsForList().range(key, 0, -1);
        if (items == null || items.isEmpty()) return Collections.emptyList();

        List<Message> messages = new ArrayList<>();
        for (String item : items) {
            try {
                messages.add(json.readValue(item, Message.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached message for conv={}", conversationId);
            }
        }
        // Redis list stores newest-first; reverse to chronological
        Collections.reverse(messages);
        // Extend TTL on access
        redis.expire(key, TTL);
        return messages;
    }

    public void pushMessage(Long conversationId, Message message) {
        String key = PREFIX + conversationId;
        try {
            String val = json.writeValueAsString(message);
            redis.opsForList().leftPush(key, val);
            redis.opsForList().trim(key, 0, MAX_CACHED - 1);
            redis.expire(key, TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for Redis cache: {}", e.getMessage());
        }
    }

    public void backfill(Long conversationId, List<Message> messages) {
        if (messages.isEmpty()) return;
        String key = PREFIX + conversationId;
        redis.delete(key);
        // Push newest first so trim keeps most recent
        List<Message> reversed = new ArrayList<>(messages);
        Collections.reverse(reversed);
        for (Message m : reversed) {
            try {
                redis.opsForList().rightPush(key, json.writeValueAsString(m));
            } catch (JsonProcessingException ignored) {}
        }
        redis.opsForList().trim(key, 0, MAX_CACHED - 1);
        redis.expire(key, TTL);
    }

    public void evict(Long conversationId) {
        redis.delete(PREFIX + conversationId);
    }
}
