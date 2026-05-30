package com.aicodehub.config;

import com.aicodehub.common.UserContext;
import com.aicodehub.common.JwtUtil;
import com.aicodehub.common.annotations.RequireRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;

        RequireRole ann = hm.getMethodAnnotation(RequireRole.class);
        if (ann == null) {
            ann = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (ann == null) return true;

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(response, 401, "未登录");
            return false;
        }

        String token = auth.substring(7);
        if (!jwtUtil.validate(token)) {
            writeError(response, 401, "Token 无效或已过期");
            return false;
        }

        String role = jwtUtil.getRole(token);
        Long userId = jwtUtil.getUserId(token);
        UserContext.set(userId, role);

        if (!Arrays.asList(ann.value()).contains(role)) {
            writeError(response, 403, "无权限");
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
            Map.of("code", code, "message", msg, "data", null)));
    }
}
