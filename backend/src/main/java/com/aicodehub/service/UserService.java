package com.aicodehub.service;

import com.aicodehub.common.dto.LoginRequest;
import com.aicodehub.common.dto.RegisterRequest;
import com.aicodehub.common.dto.ReviewRequest;
import com.aicodehub.entity.User;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

public interface UserService {
    Map<String, String> login(LoginRequest req);
    void register(RegisterRequest req);
    Page<User> listApplications(int page, int size);
    void review(ReviewRequest req, Long reviewerId);
    Map<String, String> refreshAccessToken(String rawRefreshToken);
}
