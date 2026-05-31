package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.entity.Document;
import com.aicodehub.service.MinioService;
import com.aicodehub.service.rag.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final MinioService minioService;
    private final com.aicodehub.service.rag.VectorStoreService vectorStore;

    @PostMapping
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> upload(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        Document doc = documentService.upload(UserContext.getUserId(),
            body.get("filename"), body.getOrDefault("fileType", "txt"), content);
        return Result.ok(Map.of("id", doc.getId(), "filename", doc.getFilename(), "status", doc.getStatus()));
    }

    @PostMapping("/file")
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> uploadFile(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        Long userId = UserContext.getUserId();

        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFilename(filename);
        doc.setFileType(filename.contains(".") ? filename.substring(filename.lastIndexOf(".") + 1) : "unknown");
        doc.setStatus("processing");
        var d = documentService.upload(userId, filename, doc.getFileType(), "");

        String path = minioService.upload(userId + "/" + d.getId() + "/" + filename, file);
        if (path == null) return Result.fail("MinIO 上传失败");

        try {
            String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var chunks = java.util.List.of(content);
            vectorStore.bulkIndexChunks(d.getId(),
                java.util.List.of(content.length() > 500 ?
                    content.substring(0, 500) : content),
                filename, userId);
            d.setStatus("ready");
        } catch (Exception e) {
            d.setStatus("error");
            log.error("File content processing failed: {}", e.getMessage());
        }

        return Result.ok(Map.of("id", d.getId(), "filename", filename, "status", d.getStatus()));
    }

    @GetMapping
    @PreAuthorize("hasRole('document:preview')")
    public Result<?> list() {
        return Result.ok(documentService.listByUser(UserContext.getUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> delete(@PathVariable Long id) {
        documentService.delete(id, UserContext.getUserId());
        return Result.ok();
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasRole('document:preview')")
    public Result<?> content(@PathVariable Long id) {
        return Result.ok(vectorStore.getDocContent(id));
    }
}
