package com.aicodehub.common;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    private final long refreshExpiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration:86400000}") long expiration,
                   @Value("${jwt.refresh-expiration:604800000}") long refreshExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generate(Long userId, String role) {
        return buildToken(userId, role, "access", expiration);
    }

    public String generateRefreshToken(Long userId, String role) {
        return buildToken(userId, role, "refresh", refreshExpiration);
    }

    private String buildToken(Long userId, String role, String type, long ttl) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role)
            .claim("type", type)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ttl))
            .signWith(key)
            .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(Jwts.parser()
            .verifyWith(key).build()
            .parseSignedClaims(token).getPayload().getSubject());
    }

    public String getRole(String token) {
        return Jwts.parser()
            .verifyWith(key).build()
            .parseSignedClaims(token).getPayload().get("role", String.class);
    }

    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            String type = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().get("type", String.class);
            return "access".equals(type);
        } catch (Exception e) {
            return false;
        }
    }
}
