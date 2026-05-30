package com.aicodehub.controller;

import com.aicodehub.common.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import com.aicodehub.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final AuditService auditService;

    @GetMapping
    public Result<?> dashboard() {
        return Result.ok(auditService.dashboard());
    }
}
