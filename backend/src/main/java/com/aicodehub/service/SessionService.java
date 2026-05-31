package com.aicodehub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed sliding session: each authenticated request extends the session by
 * SESSION_TTL. On logout, the session is deleted. Login creates a fresh session.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final StringRedisTemplate redis;
    private static final Duration SESSION_TTL = Duration.ofHours(1);
    private static final String PREFIX = "session:";

    public void create(Long userId) {
        redis.opsForValue().set(PREFIX + userId, "active", SESSION_TTL);
    }

    public boolean isValid(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + userId));
    }

    public void extend(Long userId) {
        redis.expire(PREFIX + userId, SESSION_TTL);
    }

    public void destroy(Long userId) {
        redis.delete(PREFIX + userId);
    }
}
