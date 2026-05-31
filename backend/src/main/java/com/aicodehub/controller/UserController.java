package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.entity.User;
import com.aicodehub.mapper.UserMapper;
import com.aicodehub.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final MinioService minioService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('user:profile')")
    public Result<?> profile() {
        User u = userMapper.selectById(UserContext.getUserId());
        if (u == null) return Result.fail("用户不存在");
        return Result.ok(Map.of("id", u.getId(), "username", u.getUsername(), "email", u.getEmail(), "avatar", u.getAvatar()));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('user:profile')")
    public Result<?> updateProfile(@RequestBody Map<String, String> body) {
        User u = userMapper.selectById(UserContext.getUserId());
        if (u == null) return Result.fail("用户不存在");
        if (body.containsKey("username")) u.setUsername(body.get("username"));
        if (body.containsKey("avatar")) u.setAvatar(body.get("avatar"));
        userMapper.updateById(u);
        return Result.ok(Map.of("username", u.getUsername(), "avatar", u.getAvatar()));
    }

    @PostMapping("/avatar")
    @PreAuthorize("hasRole('user:profile')")
    public Result<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long uid = UserContext.getUserId();
        String url = minioService.uploadAvatar(file);
        if (url == null) return Result.fail("上传失败");
        User u = userMapper.selectById(uid);
        if (u != null) { u.setAvatar(url); userMapper.updateById(u); }
        return Result.ok(Map.of("avatar", url));
    }
}
