package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','TEST','ADMIN')")
public class AgentTaskController {

    private final AgentTaskService taskService;

    @PostMapping
    public Result<?> submit(@RequestBody Map<String, String> body) {
        var task = taskService.submit(UserContext.getUserId(),
            body.get("prompt"), body.getOrDefault("modelType", "deepseek"));
        return Result.ok(Map.of("id", task.getId(), "status", "pending"));
    }

    @GetMapping
    public Result<?> list() {
        return Result.ok(taskService.listByUser(UserContext.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.ok(taskService.getById(id));
    }
}
