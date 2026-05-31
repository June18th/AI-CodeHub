package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.common.dto.ReviewRequest;
import com.aicodehub.service.PermissionService;
import com.aicodehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final PermissionService permissionService;

    @GetMapping("/applications")
    @PreAuthorize("hasRole('dashboard:view')")
    public Result<?> listApplications(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.listApplications(page, size));
    }

    @PutMapping("/review")
    @PreAuthorize("hasRole('user:review')")
    public Result<?> review(@Valid @RequestBody ReviewRequest req) {
        userService.review(req, UserContext.getUserId());
        return Result.ok();
    }

    @PostMapping("/permissions/reload")
    @PreAuthorize("hasRole('dashboard:view')")
    public Result<?> reloadPermissions() {
        permissionService.refresh();
        return Result.ok("权限缓存已刷新");
    }
}
