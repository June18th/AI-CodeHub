package com.aicodehub.config;

import com.aicodehub.common.JwtUtil;
import com.aicodehub.common.UserContext;
import com.aicodehub.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) {
        // Extract token: Authorization header first, then query param (for WebSocket)
        String token = null;
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        } else if (request.getRequestURI().startsWith("/ws/")) {
            String q = request.getQueryString();
            if (q != null) {
                for (String p : q.split("&")) {
                    if (p.startsWith("token=")) { token = p.substring(6); break; }
                }
            }
        }

        if (token == null) {
            try { chain.doFilter(request, response); } catch (Exception e) {}
            return;
        }
        if (!jwtUtil.validate(token)) {
            writeError(response, 401, "Token 无效或已过期");
            return;
        }

        Long userId = jwtUtil.getUserId(token);

        if (!sessionService.isValid(userId)) {
            writeError(response, 401, "会话已过期，请重新登录");
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserById(userId);
        String role = (userDetails instanceof UserPrincipal p) ? p.getRole() : jwtUtil.getRole(token);
        String orgTags = (userDetails instanceof UserPrincipal p) ? p.getOrgTags() : null;
        sessionService.extend(userId);
        UserContext.set(userId, role, orgTags);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Filter chain error: {}", e.getMessage());
        }
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
