package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import com.aicodehub.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','TEST','ADMIN')")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public Result<?> create(@RequestBody Map<String, String> body) {
        var c = conversationService.create(UserContext.getUserId(),
            body.getOrDefault("model", "deepseek"),
            body.getOrDefault("title", "新对话"));
        return Result.ok(Map.of("id", c.getId(), "slug", c.getSlug(), "title", c.getTitle(), "model", c.getModel()));
    }

    @GetMapping
    public Result<?> list() {
        return Result.ok(conversationService.listByUser(UserContext.getUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        conversationService.delete(id, UserContext.getUserId());
        return Result.ok();
    }

    @GetMapping("/{id}/messages")
    public Result<?> messages(@PathVariable Long id) {
        return Result.ok(conversationService.getMessages(id));
    }

    @PutMapping("/{id}/rename")
    public Result<?> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        conversationService.updateTitle(id, body.get("title"));
        return Result.ok();
    }
}
