package com.aicodehub.service.impl;

import com.aicodehub.common.JwtUtil;
import com.aicodehub.common.dto.LoginRequest;
import com.aicodehub.common.dto.RegisterRequest;
import com.aicodehub.common.dto.ReviewRequest;
import com.aicodehub.entity.User;
import com.aicodehub.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private UserMapper userMapper;
    private JwtUtil jwtUtil;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        jwtUtil = new JwtUtil("test-key-256-bits-minimum-32chars!", 900_000, 604_800_000);
        userService = new UserServiceImpl(userMapper, jwtUtil);
    }

    @Test
    void login_shouldReturnTokens() {
        User user = userWithPassword("admin", "admin123", "active");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");
        Map<String, String> result = userService.login(req);

        assertNotNull(result.get("token"));
        assertNotNull(result.get("refreshToken"));
        assertEquals("admin", result.get("role"));
        assertEquals("admin", result.get("username"));
    }

    @Test
    void login_shouldThrowOnWrongPassword() {
        User user = userWithPassword("admin", "correct", "active");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");
        assertThrows(IllegalArgumentException.class, () -> userService.login(req));
    }

    @Test
    void login_shouldThrowOnInactiveUser() {
        User user = userWithPassword("newuser", "pass", "pending");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("newuser");
        req.setPassword("pass");
        assertThrows(IllegalArgumentException.class, () -> userService.login(req));
    }

    @Test
    void register_shouldThrowOnDuplicateUsername() {
        when(userMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);
        RegisterRequest req = new RegisterRequest();
        req.setUsername("taken");
        req.setEmail("a@b.com");
        req.setPassword("pass");
        assertThrows(IllegalArgumentException.class, () -> userService.register(req));
    }

    @Test
    void register_shouldInsertUser() {
        when(userMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        RegisterRequest req = new RegisterRequest();
        req.setUsername("new");
        req.setEmail("new@test.com");
        req.setPassword("pass123");
        req.setApplyReason("test");
        assertDoesNotThrow(() -> userService.register(req));
        verify(userMapper).insert(argThat(u ->
            "user".equals(u.getRole()) && "pending".equals(u.getStatus())));
    }

    @Test
    void review_shouldApproveAsTest() {
        User user = new User();
        user.setId(10L);
        user.setStatus("pending");
        when(userMapper.selectById(10L)).thenReturn(user);

        ReviewRequest req = new ReviewRequest();
        req.setUserId(10L);
        req.setAction("approve");
        req.setRole("test");
        userService.review(req, 1L);

        assertEquals("active", user.getStatus());
        assertEquals("test", user.getRole());
        assertEquals(1L, user.getReviewedBy());
    }

    @Test
    void review_shouldReject() {
        User user = new User();
        user.setId(10L);
        user.setStatus("pending");
        when(userMapper.selectById(10L)).thenReturn(user);

        ReviewRequest req = new ReviewRequest();
        req.setUserId(10L);
        req.setAction("reject");
        userService.review(req, 1L);

        assertEquals("rejected", user.getStatus());
    }

    @Test
    void refreshAccessToken_shouldReturnNewToken() {
        User user = new User();
        user.setId(1L);
        user.setRefreshToken("$2a$10$placeholder");
        when(userMapper.selectById(1L)).thenReturn(user);

        String refreshToken = jwtUtil.generateRefreshToken(1L, "admin");
        user.setRefreshToken(new BCryptPasswordEncoder().encode(refreshToken));

        Map<String, String> result = userService.refreshAccessToken(refreshToken);
        assertNotNull(result.get("token"));
        assertEquals("admin", result.get("role"));
    }

    @Test
    void refreshAccessToken_shouldThrowOnInvalidToken() {
        assertThrows(IllegalArgumentException.class,
            () -> userService.refreshAccessToken("invalid-token"));
    }

    private User userWithPassword(String username, String rawPassword, String status) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(new BCryptPasswordEncoder().encode(rawPassword));
        user.setRole("admin");
        user.setStatus(status);
        return user;
    }
}
