package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import com.aicodehub.entity.Document;
import com.aicodehub.entity.FileUpload;
import com.aicodehub.service.ChunkedUploadService;
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
    private final ChunkedUploadService chunkedUploadService;
    private final com.aicodehub.service.rag.VectorStoreService vectorStore;

    // ── Text upload (async, same pipeline as file) ──

    @PostMapping
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> upload(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String filename = body.getOrDefault("filename", "untitled.txt");
        String fileType = body.getOrDefault("fileType", "txt");
        String visibility = body.getOrDefault("visibility", "PRIVATE");
        String orgTag = body.getOrDefault("orgTag", UserContext.getOrgTags() != null ?
            UserContext.getOrgTags().split(",")[0].trim() : null);

        Document doc = chunkedUploadService.uploadText(UserContext.getUserId(),
            filename, fileType, content, visibility, orgTag);
        return Result.ok(Map.of("id", doc.getId(), "filename", doc.getFilename(),
            "visibility", doc.getVisibility(), "status", doc.getStatus()));
    }

    // ── Chunked file upload ──

    /** Step 1: Initialize upload session */
    @PostMapping("/chunk/init")
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> initChunkedUpload(@RequestBody Map<String, Object> body) {
        String fileMd5 = (String) body.get("fileMd5");
        String filename = (String) body.get("filename");
        long fileSize = ((Number) body.get("fileSize")).longValue();
        int totalChunks = ((Number) body.get("totalChunks")).intValue();
        String fileType = (String) body.getOrDefault("fileType", null);
        String visibility = (String) body.getOrDefault("visibility", "PRIVATE");
        String orgTag = (String) body.getOrDefault("orgTag",
            UserContext.getOrgTags() != null ? UserContext.getOrgTags().split(",")[0].trim() : null);

        FileUpload fu = chunkedUploadService.initUpload(
            fileMd5, filename, fileSize, totalChunks, fileType,
            UserContext.getUserId(), visibility, orgTag);
        return Result.ok(Map.of("id", fu.getId(), "fileMd5", fu.getFileMd5(), "status", fu.getStatus()));
    }

    /** Step 2: Upload a single chunk (JSON body with base64) */
    @PostMapping("/chunk")
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> uploadChunk(@RequestBody Map<String, String> body) {
        try {
            String fileMd5 = body.get("fileMd5");
            int chunkIndex = Integer.parseInt(body.get("chunkIndex"));
            String dataBase64 = body.get("data"); // base64-encoded chunk bytes
            byte[] data = java.util.Base64.getDecoder().decode(dataBase64);
            boolean ok = chunkedUploadService.uploadChunk(fileMd5, chunkIndex, data);
            return ok ? Result.ok() : Result.fail("Chunk upload failed");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** Step 3: Check upload status (resume support) */
    @GetMapping("/chunk/{fileMd5}/status")
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> chunkStatus(@PathVariable String fileMd5) {
        return Result.ok(chunkedUploadService.getStatus(fileMd5));
    }

    /** Step 4: Merge all chunks */
    @PostMapping("/chunk/{fileMd5}/merge")
    @PreAuthorize("hasRole('knowledge:upload')")
    public Result<?> mergeChunks(@PathVariable String fileMd5) {
        Document doc = chunkedUploadService.mergeChunks(fileMd5);
        return Result.ok(Map.of("id", doc.getId(), "filename", doc.getFilename(),
            "status", doc.getStatus(), "message", "File merged, processing started asynchronously"));
    }

    // ── Legacy endpoints ──

    @GetMapping
    @PreAuthorize("hasRole('document:preview')")
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size) {
        var docs = documentService.listByUser(UserContext.getUserId(), page, size);
        long total = documentService.countByUser(UserContext.getUserId());
        return Result.ok(Map.of("records", docs, "total", total, "page", page, "size", size));
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
        var doc = documentService.getById(id);
        if (doc == null) return Result.fail("Document not found");

        // MinIO original (chunked upload)
        if (doc.getMinioPath() != null) {
            String content = minioService.downloadAsString(doc.getMinioPath());
            if (content != null) return Result.ok(content);
        }

        // Fallback: ES chunks (text upload, or old docs)
        var chunks = vectorStore.getDocContent(id);
        if (!chunks.isEmpty()) return Result.ok(chunks);

        return Result.fail("Content not found");
    }
}
