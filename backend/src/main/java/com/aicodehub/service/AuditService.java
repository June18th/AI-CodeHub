package com.aicodehub.service;

import com.aicodehub.entity.AuditLog;
import com.aicodehub.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper mapper;
    private final com.aicodehub.mapper.UserMapper userMapper;

    public void log(Long userId, String username, String model, String endpoint,
                    int inputTokens, int outputTokens, int latencyMs,
                    String status, String errorMsg) {
        log(userId, username, model, endpoint, inputTokens, outputTokens, latencyMs, status, errorMsg, null);
    }

    public void log(Long userId, String username, String model, String endpoint,
                    int inputTokens, int outputTokens, int latencyMs,
                    String status, String errorMsg, String tokenBreakdown) {
        if (username == null && userId != null) {
            var user = userMapper.selectById(userId);
            username = user != null ? user.getUsername() : null;
        }
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setModel(model);
        log.setEndpoint(endpoint);
        log.setInputTokens(inputTokens);
        log.setOutputTokens(outputTokens);
        log.setTokenBreakdown(tokenBreakdown);
        log.setLatencyMs(latencyMs);
        log.setStatus(status);
        log.setErrorMsg(errorMsg);
        mapper.insert(log);
    }

    public Map<String, Object> dashboard() {
        int calls = orZero(mapper.todayCalls());
        int errors = orZero(mapper.todayErrors());
        return Map.of(
            "todayCalls", calls,
            "activeUsers", orZero(mapper.activeUsers24h()),
            "todayTokens", orZero(mapper.todayTokens()),
            "todayErrors", errors,
            "avgLatency", orZero(mapper.avgLatency()),
            "errorRate", calls > 0 ? Math.round(errors * 10000.0 / calls) / 100.0 : 0,
            "modelUsage", mapper.modelUsage24h(),
            "hourlyTrend", mapper.hourlyTrend()
        );
    }

    private int orZero(Integer v) { return v == null ? 0 : v; }
}
