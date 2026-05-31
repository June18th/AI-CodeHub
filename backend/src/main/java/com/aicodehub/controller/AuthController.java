package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.common.dto.LoginRequest;
import com.aicodehub.common.dto.RegisterRequest;
import com.aicodehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(userService.login(req));
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.ok("申请已提交，请等待管理员审核");
    }

    @PostMapping("/refresh")
    public Result<?> refresh(@RequestBody java.util.Map<String, String> body) {
        return Result.ok(userService.refreshAccessToken(body.get("refreshToken")));
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        userService.logout(UserContext.getUserId());
        return Result.ok();
    }
}
