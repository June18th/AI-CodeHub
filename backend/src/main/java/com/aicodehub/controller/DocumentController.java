package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import com.aicodehub.entity.Document;
import com.aicodehub.service.MinioService;
import com.aicodehub.service.rag.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','TEST','ADMIN')")
public class DocumentController {

    private final DocumentService documentService;
    private final MinioService minioService;
    private final KafkaTemplate<String, String> kafka;
    private final com.aicodehub.service.rag.VectorStoreService vectorStore;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Text paste upload */
    @PostMapping
    public Result<?> upload(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        Document doc = documentService.upload(UserContext.getUserId(),
            body.get("filename"), body.getOrDefault("fileType", "txt"), content);
        // Send to Kafka for async embedding + ES indexing
        sendToKafka(doc.getId(), UserContext.getUserId(), doc.getFilename(), content);
        return Result.ok(Map.of("id", doc.getId(), "filename", doc.getFilename(), "status", "processing"));
    }

    /** File upload to MinIO + Kafka async processing */
    @PostMapping("/file")
    public Result<?> uploadFile(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        Long userId = UserContext.getUserId();

        // Save doc metadata
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFilename(filename);
        doc.setFileType(filename.contains(".") ? filename.substring(filename.lastIndexOf(".") + 1) : "unknown");
        doc.setStatus("processing");
        // Store in MySQL via service
        var d = documentService.upload(userId, filename, doc.getFileType(), "");

        // Upload to MinIO
        String path = minioService.upload(userId + "/" + d.getId() + "/" + filename, file);
        if (path == null) return Result.fail("MinIO 上传失败");

        // Read content and send to Kafka for async embedding
        try {
            String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            sendToKafka(d.getId(), userId, filename, content);
        } catch (Exception e) {
            // For binary files, just index the filename
            sendToKafka(d.getId(), userId, filename, "[文件: " + filename + ", 路径: " + path + "]");
        }

        return Result.ok(Map.of("id", d.getId(), "filename", filename, "status", "processing"));
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

    @GetMapping("/{id}/content")
    public Result<?> content(@PathVariable Long id) {
        return Result.ok(vectorStore.getDocContent(id));
    }

    private void sendToKafka(Long docId, Long userId, String filename, String content) {
        try {
            Map<String, Object> event = Map.of(
                "docId", docId, "userId", userId,
                "filename", filename, "content", content
            );
            kafka.send("doc-processing", mapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Kafka send failed: {}", e.getMessage());
        }
    }
}
