package com.aicodehub.config;

import com.aicodehub.entity.User;
import com.aicodehub.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        // Init admin
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

        // Seed permissions
        seedPermission("dashboard:view", "运营监控查看");
        seedPermission("user:review", "用户审核");
        seedPermission("model:config", "模型配置管理");
        seedPermission("knowledge:upload", "知识库上传");
        seedPermission("workflow:create", "工作流创建");
        seedPermission("document:preview", "文档内容预览");

        // admin gets all
        mapRole("admin", "dashboard:view");
        mapRole("admin", "user:review");
        mapRole("admin", "model:config");
        mapRole("admin", "knowledge:upload");
        mapRole("admin", "workflow:create");
        mapRole("admin", "document:preview");
        // test gets agent + knowledge + workflow + doc preview
        mapRole("test", "knowledge:upload");
        mapRole("test", "workflow:create");
        mapRole("test", "document:preview");
        mapRole("test", "model:config");
        // user: knowledge + doc + workflow + model config (own keys)
        mapRole("user", "knowledge:upload");
        mapRole("user", "document:preview");
        mapRole("user", "workflow:create");
        mapRole("user", "model:config");
    }

    private void seedPermission(String code, String name) {
        int cnt = jdbc.queryForObject("SELECT COUNT(*) FROM permission WHERE code = ?", Integer.class, code);
        if (cnt == 0) {
            jdbc.update("INSERT INTO permission (code, name) VALUES (?, ?)", code, name);
        }
    }

    private void mapRole(String role, String code) {
        Long pid = jdbc.queryForObject("SELECT id FROM permission WHERE code = ?", Long.class, code);
        if (pid != null) {
            int cnt = jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role = ? AND permission_id = ?", Integer.class, role, pid);
            if (cnt == 0) {
                jdbc.update("INSERT INTO role_permission (role, permission_id) VALUES (?, ?)", role, pid);
            }
        }
    }
}
