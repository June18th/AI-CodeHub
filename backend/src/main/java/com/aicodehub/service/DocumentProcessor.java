package com.aicodehub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aicodehub.service.rag.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessor {

    private final VectorStoreService vectorStore;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final int CHUNK_SIZE = 500;

    @KafkaListener(topics = "doc-processing", groupId = "doc-processor")
    public void process(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            Long docId = node.get("docId").asLong();
            Long userId = node.get("userId").asLong();
            String filename = node.get("filename").asText();
            String content = node.get("content").asText();

            log.info("Processing doc {}: {} ({} chars)", docId, filename, content.length());
            List<String> chunks = splitChunks(content, CHUNK_SIZE);
            for (int i = 0; i < chunks.size(); i++) {
                vectorStore.indexChunk(docId, i, chunks.get(i), filename, userId);
            }
            log.info("Doc {} processed: {} chunks indexed", docId, chunks.size());
        } catch (Exception e) {
            log.error("Document processing failed: {}", e.getMessage());
        }
    }

    private List<String> splitChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            int end = Math.min(i + size, text.length());
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }
}
