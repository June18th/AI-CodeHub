package com.aicodehub.config;

import com.aicodehub.entity.User;
import com.aicodehub.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@aicodehub.com");
            admin.setPassword(encoder.encode("admin123"));
            admin.setRole("admin");
            admin.setStatus("active");
            userMapper.insert(admin);
            log.info("Default admin created: admin / admin123");
        }
    }
}
