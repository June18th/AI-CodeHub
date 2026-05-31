package com.aicodehub.service;

import com.aicodehub.mapper.PermissionMapper;
import com.aicodehub.mapper.RolePermissionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    private static final String PREFIX = "rbac:perm:";
    private static final Duration TTL = Duration.ofHours(24);

    @PostConstruct
    public void init() {
        refresh();
    }

    public List<String> getPermissionsByRole(String role) {
        String cached = redis.opsForValue().get(PREFIX + role);
        if (cached != null) {
            try {
                return json.readValue(cached, List.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse permission cache for role={}", role);
            }
        }
        List<String> perms = permissionMapper.findCodesByRole(role);
        cachePermissions(role, perms);
        return perms;
    }

    public void refresh() {
        List<String> roles = rolePermissionMapper.selectList(null).stream()
                .map(rp -> rp.getRole())
                .distinct()
                .collect(Collectors.toList());

        for (String role : roles) {
            List<String> perms = permissionMapper.findCodesByRole(role);
            cachePermissions(role, perms);
        }
        log.info("RBAC permissions refreshed for {} roles", roles.size());
    }

    private void cachePermissions(String role, List<String> perms) {
        try {
            redis.opsForValue().set(PREFIX + role, json.writeValueAsString(perms), TTL);
        } catch (Exception e) {
            log.error("Failed to cache permissions for role={}", role, e);
        }
    }
}
