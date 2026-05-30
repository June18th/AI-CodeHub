package com.aicodehub.service.impl;

import com.aicodehub.common.JwtUtil;
import com.aicodehub.common.dto.LoginRequest;
import com.aicodehub.common.dto.RegisterRequest;
import com.aicodehub.common.dto.ReviewRequest;
import com.aicodehub.entity.User;
import com.aicodehub.mapper.UserMapper;
import com.aicodehub.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, String> login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, req.getUsername()));
        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号未激活，请等待管理员审核");
        }
        String token = jwtUtil.generate(user.getId(), user.getRole());
        return Map.of("token", token, "role", user.getRole(), "username", user.getUsername());
    }

    @Override
    public void register(RegisterRequest req) {
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()))) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()))) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setRole("applicant");
        user.setStatus("pending");
        user.setApplyReason(req.getApplyReason());
        userMapper.insert(user);
    }

    @Override
    public Page<User> listApplications(int page, int size) {
        return userMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<User>()
                .eq(User::getStatus, "pending")
                .orderByAsc(User::getCreatedAt));
    }

    @Override
    public void review(ReviewRequest req, Long reviewerId) {
        User user = userMapper.selectById(req.getUserId());
        if (user == null || !"pending".equals(user.getStatus())) {
            throw new IllegalArgumentException("用户不存在或已审核");
        }
        if ("approve".equals(req.getAction())) {
            String role = req.getRole() != null ? req.getRole() : "user";
            if (!"user".equals(role) && !"beta".equals(role)) {
                throw new IllegalArgumentException("角色只能为 user 或 beta");
            }
            user.setRole(role);
            user.setStatus("active");
        } else {
            user.setStatus("rejected");
        }
        user.setReviewedBy(reviewerId);
        user.setReviewedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
