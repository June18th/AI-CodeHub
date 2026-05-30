package com.aicodehub.service.rag;

import com.aicodehub.entity.Document;
import com.aicodehub.mapper.DocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final VectorStoreService vectorStore;

    private static final int CHUNK_SIZE = 500;

    @PostConstruct
    public void init() {
        vectorStore.ensureIndex();
    }

    public Document upload(Long userId, String filename, String fileType, String content) {
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFilename(filename);
        doc.setFileType(fileType);
        doc.setStatus("processing");
        documentMapper.insert(doc);

        List<String> chunks = splitChunks(content, CHUNK_SIZE);
        for (int i = 0; i < chunks.size(); i++) {
            vectorStore.indexChunk(doc.getId(), i, chunks.get(i), filename, userId);
        }

        doc.setStatus("ready");
        documentMapper.updateById(doc);
        log.info("Document {} uploaded: {} chunks indexed to ES", doc.getId(), chunks.size());
        return doc;
    }

    public List<Document> listByUser(Long userId) {
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
            .eq(Document::getUserId, userId)
            .orderByDesc(Document::getCreatedAt));
    }

    public void delete(Long docId, Long userId) {
        Document doc = documentMapper.selectById(docId);
        if (doc != null && doc.getUserId().equals(userId)) {
            vectorStore.deleteDoc(docId);
            documentMapper.deleteById(docId);
        }
    }

    /** Vector search via ES */
    public List<String> retrieve(Long userId, String query, int topK) {
        List<Map<String, Object>> hits = vectorStore.search(userId, query, topK);
        if (hits.isEmpty()) return List.of("未找到相关内容");

        return hits.stream().map(h -> {
            String filename = (String) h.get("filename");
            String content = (String) h.get("content");
            double score = (double) h.get("score");
            String snippet = content.length() > 300 ? content.substring(0, 300) + "..." : content;
            return String.format("[%s] (相似度 %.2f) %s", filename, score, snippet);
        }).collect(Collectors.toList());
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
