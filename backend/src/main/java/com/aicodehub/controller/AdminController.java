package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.common.annotations.RequireRole;
import com.aicodehub.common.dto.ReviewRequest;
import com.aicodehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@RequireRole("admin")
public class AdminController {

    private final UserService userService;

    @GetMapping("/applications")
    public Result<?> listApplications(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.listApplications(page, size));
    }

    @PutMapping("/review")
    public Result<?> review(@Valid @RequestBody ReviewRequest req) {
        userService.review(req, UserContext.getUserId());
        return Result.ok();
    }
}
