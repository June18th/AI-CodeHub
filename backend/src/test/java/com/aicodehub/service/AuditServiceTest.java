package com.aicodehub.service;

import com.aicodehub.entity.User;
import com.aicodehub.mapper.AuditLogMapper;
import com.aicodehub.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditLogMapper auditMapper;
    private UserMapper userMapper;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditMapper = mock(AuditLogMapper.class);
        userMapper = mock(UserMapper.class);
        auditService = new AuditService(auditMapper, userMapper);
    }

    @Test
    void log_shouldResolveUsernameWhenUserIdProvided() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        when(userMapper.selectById(1L)).thenReturn(user);

        auditService.log(1L, null, "deepseek", "/api/chat", 100, 200, 500, "success", null);
        verify(userMapper).selectById(1L);
        verify(auditMapper).insert(argThat(log ->
            log.getUserId() == 1L && "admin".equals(log.getUsername())));
    }

    @Test
    void log_shouldKeepNullUsernameWhenUserIdIsNull() {
        auditService.log(null, null, "deepseek", "/api/chat", 0, 0, 0, "error", "fail");
        verify(auditMapper).insert(argThat(log ->
            log.getUserId() == null && log.getUsername() == null));
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void dashboard_shouldReturnAllMetrics() {
        when(auditMapper.todayCalls()).thenReturn(10);
        when(auditMapper.activeUsers24h()).thenReturn(3);
        when(auditMapper.todayTokens()).thenReturn(5000);
        when(auditMapper.todayErrors()).thenReturn(2);
        when(auditMapper.avgLatency()).thenReturn(250);
        when(auditMapper.modelUsage24h()).thenReturn(List.of());
        when(auditMapper.hourlyTrend()).thenReturn(List.of());
        when(auditMapper.recentLogs(20)).thenReturn(List.of());

        Map<String, Object> dash = auditService.dashboard();
        assertEquals(10, dash.get("todayCalls"));
        assertEquals(3, dash.get("activeUsers"));
        assertEquals(5000, dash.get("todayTokens"));
        assertEquals(2, dash.get("todayErrors"));
        assertEquals(250, dash.get("avgLatency"));
        assertEquals(20.0, dash.get("errorRate")); // 2/10 * 100
    }

    @Test
    void dashboard_shouldHandleNullValues() {
        when(auditMapper.todayCalls()).thenReturn(null);
        when(auditMapper.todayTokens()).thenReturn(null);
        Map<String, Object> dash = auditService.dashboard();
        assertEquals(0, dash.get("todayCalls"));
        assertEquals(0, dash.get("todayTokens"));
        assertEquals(0.0, dash.get("errorRate"));
    }
}
