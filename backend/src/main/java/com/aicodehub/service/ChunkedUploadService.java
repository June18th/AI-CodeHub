package com.aicodehub.service;

import com.aicodehub.entity.Document;
import com.aicodehub.entity.FileUpload;
import com.aicodehub.mapper.DocumentMapper;
import com.aicodehub.mapper.FileUploadMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkedUploadService {

    private final MinioClient minioClient;
    private final RedisTemplate<String, String> redis;
    private final FileUploadMapper fileUploadMapper;
    private final DocumentMapper documentMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String BUCKET = "documents";
    private static final String CHUNK_PREFIX = "chunks/";
    private static final int CHUNK_REDIS_TTL_HOURS = 48;
    private static final String TOPIC = "document-ready";

    /** Text upload: save to MinIO → create doc → Kafka async processing */
    public Document uploadText(Long userId, String filename, String fileType,
                                String content, String visibility, String orgTag) {
        // Write content to MinIO
        String objectName = userId + "/" + System.currentTimeMillis() + "/" + filename;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(BUCKET).object(objectName)
                .stream(new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, -1)
                .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save text to MinIO", e);
        }

        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFilename(filename);
        doc.setFileType(fileType);
        doc.setVisibility(visibility != null ? visibility.toUpperCase() : "PRIVATE");
        doc.setOrgTag(orgTag);
        doc.setStatus("processing");
        doc.setMinioPath(objectName);
        documentMapper.insert(doc);

        // Kafka async
        String kafkaMsg = "{\"documentId\":" + doc.getId() + ",\"object\":\"" + objectName + "\"}";
        kafkaTemplate.send(TOPIC, doc.getId().toString(), kafkaMsg)
            .whenComplete((result, ex) -> {
                if (ex != null) log.error("Kafka send failed: {}", ex.getMessage());
                else log.info("Text upload queued: doc#{} offset={}", doc.getId(), result.getRecordMetadata().offset());
            });

        log.info("Text upload: {} -> doc#{} ({} bytes), queued for async processing", filename, doc.getId(), content.length());
        return doc;
    }

    /** Initialize upload session */
    public FileUpload initUpload(String fileMd5, String filename, long fileSize, int totalChunks,
                                  String fileType, Long userId, String visibility, String orgTag) {
        var existing = fileUploadMapper.selectOne(new LambdaQueryWrapper<FileUpload>()
            .eq(FileUpload::getFileMd5, fileMd5));
        if (existing != null) {
            if ("completed".equals(existing.getStatus())) return existing;
            return existing; // still uploading, resume
        }

        FileUpload fu = new FileUpload();
        fu.setFileMd5(fileMd5);
        fu.setFilename(filename);
        fu.setFileSize(fileSize);
        fu.setTotalChunks(totalChunks);
        fu.setFileType(fileType != null ? fileType :
            filename.contains(".") ? filename.substring(filename.lastIndexOf(".") + 1) : "txt");
        fu.setUserId(userId);
        fu.setVisibility(visibility);
        fu.setOrgTag(orgTag);
        fu.setStatus("uploading");
        fileUploadMapper.insert(fu);
        log.info("Upload session created: {} {} ({} chunks)", fileMd5, filename, totalChunks);
        return fu;
    }

    /** Upload a single chunk to MinIO and mark Redis bitmap */
    public boolean uploadChunk(String fileMd5, int chunkIndex, byte[] data) {
        String key = redisKey(fileMd5);
        String objectName = CHUNK_PREFIX + fileMd5 + "/" + chunkIndex;

        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(BUCKET)
                .object(objectName)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .build());
            redis.opsForValue().setBit(key, chunkIndex, true);
            redis.expire(key, CHUNK_REDIS_TTL_HOURS, TimeUnit.HOURS);
            return true;
        } catch (Exception e) {
            log.error("Chunk upload failed: {}[{}] {}", fileMd5, chunkIndex, e.getMessage());
            return false;
        }
    }

    /** Get upload status: bitmap of uploaded chunks */
    public Map<String, Object> getStatus(String fileMd5) {
        FileUpload fu = fileUploadMapper.selectOne(new LambdaQueryWrapper<FileUpload>()
            .eq(FileUpload::getFileMd5, fileMd5));
        if (fu == null) return Map.of("exists", false);

        String key = redisKey(fileMd5);
        int total = fu.getTotalChunks();
        int uploaded = 0;
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (Boolean.TRUE.equals(redis.opsForValue().getBit(key, i))) {
                uploaded++;
            } else {
                missing.add(i);
            }
        }

        return Map.of(
            "exists", true,
            "fileMd5", fileMd5,
            "filename", fu.getFilename(),
            "totalChunks", total,
            "uploadedChunks", uploaded,
            "missingChunks", missing,
            "status", fu.getStatus()
        );
    }

    /** Merge all chunks via MinIO composeObject */
    public Document mergeChunks(String fileMd5) {
        FileUpload fu = fileUploadMapper.selectOne(new LambdaQueryWrapper<FileUpload>()
            .eq(FileUpload::getFileMd5, fileMd5));
        if (fu == null) throw new RuntimeException("Upload session not found: " + fileMd5);

        String key = redisKey(fileMd5);
        int total = fu.getTotalChunks();

        // Verify all chunks present
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (!Boolean.TRUE.equals(redis.opsForValue().getBit(key, i))) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) throw new RuntimeException("Chunks missing: " + missing);

        fu.setStatus("merging");
        fileUploadMapper.updateById(fu);

        try {
            // Build compose sources
            String destObject = fu.getUserId() + "/" + System.currentTimeMillis() + "/" + fu.getFilename();
            List<ComposeSource> sources = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                sources.add(ComposeSource.builder()
                    .bucket(BUCKET)
                    .object(CHUNK_PREFIX + fileMd5 + "/" + i)
                    .build());
            }

            // Compose directly (total chunks should be < 1000 for practical file sizes)
            if (sources.size() > 1000) {
                throw new RuntimeException("Too many chunks: " + sources.size() + " (max 1000)");
            }

            minioClient.composeObject(ComposeObjectArgs.builder()
                .bucket(BUCKET).object(destObject).sources(sources).build());

            // Delete chunks
            for (int i = 0; i < total; i++) {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(BUCKET)
                        .object(CHUNK_PREFIX + fileMd5 + "/" + i)
                        .build());
                } catch (Exception ignored) {}
            }

            // Create document record with MinIO path
            Document doc = new Document();
            doc.setUserId(fu.getUserId());
            doc.setFilename(fu.getFilename());
            doc.setFileType(fu.getFileType());
            doc.setVisibility(fu.getVisibility());
            doc.setOrgTag(fu.getOrgTag());
            doc.setStatus("processing");
            doc.setMinioPath(destObject);
            documentMapper.insert(doc);

            // Link and complete
            fu.setStatus("completed");
            fu.setDocumentId(doc.getId());
            fileUploadMapper.updateById(fu);

            // Clean up Redis
            redis.delete(key);

            // Kafka async: send message, consumer handles download+chunk+embed+ES
            String kafkaMsg = "{\"documentId\":" + doc.getId() + ",\"fileMd5\":\"" + fileMd5 + "\",\"object\":\"" + destObject + "\"}";
            kafkaTemplate.send(TOPIC, doc.getId().toString(), kafkaMsg)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Kafka send failed: {}", ex.getMessage());
                    else log.info("Kafka sent: {} offset={}", fileMd5, result.getRecordMetadata().offset());
                });

            log.info("File merged: {} -> doc#{} ({} chunks), queued for async processing", fileMd5, doc.getId(), total);
            return doc;

        } catch (Exception e) {
            fu.setStatus("failed");
            fileUploadMapper.updateById(fu);
            log.error("Merge failed for {}: {}", fileMd5, e.getMessage());
            throw new RuntimeException("Merge failed: " + e.getMessage(), e);
        }
    }

    private String redisKey(String fileMd5) {
        return "upload:chunks:" + fileMd5;
    }
}
