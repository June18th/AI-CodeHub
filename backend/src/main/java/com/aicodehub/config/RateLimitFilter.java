package com.aicodehub.config;

import com.aicodehub.common.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!props.isEnabled() || !request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        int rpm = resolveRpm(path, request);
        String key = resolveKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(rpm));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                Map.of("code", 429, "message", "请求过于频繁，请稍后再试", "data", null));
        }
    }

    private int resolveRpm(String path, HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            try {
                if (jwtUtil.validate(token)) {
                    String role = jwtUtil.getRole(token);
                    if ("admin".equals(role)) return props.getAdminRpm();
                }
            } catch (Exception ignored) {}
        }
        if (path.contains("/chat/")) return props.getChatRpm();
        if (path.contains("/agent/")) return props.getAgentRpm();
        return props.getDefaultRpm();
    }

    private String resolveKey(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            try {
                if (jwtUtil.validate(token)) {
                    return "uid:" + jwtUtil.getUserId(token);
                }
            } catch (Exception ignored) {}
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return "ip:" + ip;
    }

    private Bucket createBucket(int rpm) {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(rpm)
                .refillIntervally(rpm, Duration.ofMinutes(1))
                .build())
            .build();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
