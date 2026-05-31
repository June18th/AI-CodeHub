package com.aicodehub.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-256-bits-minimum-length!!", 900_000, 604_800_000);
    }

    @Test
    void generate_shouldCreateValidAccessToken() {
        String token = jwtUtil.generate(1L, "admin");
        assertNotNull(token);
        assertTrue(jwtUtil.validate(token));
        assertTrue(jwtUtil.isAccessToken(token));
        assertEquals(1L, jwtUtil.getUserId(token));
        assertEquals("admin", jwtUtil.getRole(token));
    }

    @Test
    void generateRefreshToken_shouldCreateValidRefreshToken() {
        String token = jwtUtil.generateRefreshToken(1L, "admin");
        assertNotNull(token);
        assertTrue(jwtUtil.validate(token));
        assertFalse(jwtUtil.isAccessToken(token));
        assertEquals(1L, jwtUtil.getUserId(token));
    }

    @Test
    void validate_shouldRejectTamperedToken() {
        String token = jwtUtil.generate(1L, "user");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertFalse(jwtUtil.validate(tampered));
    }

    @Test
    void validate_shouldRejectEmptyToken() {
        assertFalse(jwtUtil.validate(""));
        assertFalse(jwtUtil.validate(null));
    }

    @Test
    void getUserId_shouldReturnCorrectId() {
        assertEquals(42L, jwtUtil.getUserId(jwtUtil.generate(42L, "test")));
    }

    @Test
    void getRole_shouldReturnCorrectRole() {
        assertEquals("test", jwtUtil.getRole(jwtUtil.generate(99L, "test")));
    }
}
