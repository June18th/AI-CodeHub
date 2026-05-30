package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.common.annotations.RequireRole;
import com.aicodehub.service.rag.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@RequireRole({"user", "beta", "admin"})
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public Result<?> upload(@RequestBody Map<String, String> body) {
        var doc = documentService.upload(UserContext.getUserId(),
            body.get("filename"), body.getOrDefault("fileType", "txt"), body.get("content"));
        return Result.ok(Map.of("id", doc.getId(), "filename", doc.getFilename(), "status", doc.getStatus()));
    }

    @GetMapping
    public Result<?> list() {
        return Result.ok(documentService.listByUser(UserContext.getUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        documentService.delete(id, UserContext.getUserId());
        return Result.ok();
    }
}
