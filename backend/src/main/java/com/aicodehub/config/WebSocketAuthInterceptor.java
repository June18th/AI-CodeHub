package com.aicodehub.config;

import com.aicodehub.common.JwtUtil;
import com.aicodehub.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        log.info("WebSocket handshake request: URI={}, query={}", request.getURI(), query);
        if (query == null) { log.warn("WebSocket handshake rejected: no query string"); return false; }
        String token = null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring(6);
                break;
            }
        }
        if (token == null || !jwtUtil.validate(token)) {
            log.warn("WebSocket handshake rejected: invalid token");
            return false;
        }
        Long userId = jwtUtil.getUserId(token);
        if (!sessionService.isValid(userId)) {
            log.warn("WebSocket handshake rejected: session expired for {}", userId);
            return false;
        }
        attributes.put("userId", userId);
        attributes.put("role", jwtUtil.getRole(token));
        sessionService.extend(userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}
