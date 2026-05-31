package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.entity.AuditLog;
import com.aicodehub.mapper.AuditLogMapper;
import com.aicodehub.service.AuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AuditService auditService;
    private final AuditLogMapper auditLogMapper;

    @GetMapping
    @PreAuthorize("hasRole('dashboard:view')")
    public Result<?> dashboard() {
        return Result.ok(auditService.dashboard());
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('dashboard:view')")
    public Result<?> recentLogs(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Page<AuditLog> p = auditLogMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getCreatedAt));
        return Result.ok(Map.of("records", p.getRecords(), "total", p.getTotal(), "pages", p.getPages()));
    }
}
