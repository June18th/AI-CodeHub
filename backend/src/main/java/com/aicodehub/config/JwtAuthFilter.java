package com.aicodehub.config;

import com.aicodehub.common.JwtUtil;
import com.aicodehub.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/", "/api/v1/chat/", "/api/v1/agent/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) {
        String path = request.getRequestURI();
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        if (isPublic) {
            try { chain.doFilter(request, response); } catch (Exception e) {}
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(response, 401, "未登录");
            return;
        }

        String token = auth.substring(7);
        if (!jwtUtil.validate(token)) {
            writeError(response, 401, "Token 无效或已过期");
            return;
        }

        Long userId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
        );

        UserContext.set(userId, role);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try { chain.doFilter(request, response); } catch (Exception e) {}
    }

    private void writeError(HttpServletResponse response, int code, String msg) {
        try {
            response.setStatus(code);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), Map.of("code", code, "message", msg, "data", null));
        } catch (Exception ignored) {}
    }
}
