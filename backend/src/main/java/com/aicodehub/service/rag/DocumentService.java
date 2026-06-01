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
    private final com.aicodehub.service.OrgTagService orgTagService;

    private static final int CHUNK_SIZE = 500;

    @PostConstruct
    public void init() {
        vectorStore.ensureIndex();
        vectorStore.ensureSummaryIndex();
    }

    public Document upload(Long userId, String filename, String fileType, String content) {
        return upload(userId, filename, fileType, content, "PRIVATE", null);
    }

    public Document upload(Long userId, String filename, String fileType, String content,
                           String visibility) {
        return upload(userId, filename, fileType, content, visibility, null);
    }

    public Document upload(Long userId, String filename, String fileType, String content,
                           String visibility, String orgTag) {
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFilename(filename);
        doc.setFileType(fileType);
        doc.setVisibility(visibility != null ? visibility.toUpperCase() : "PRIVATE");
        doc.setOrgTag(orgTag);
        doc.setStatus("processing");
        documentMapper.insert(doc);

        List<String> chunks = splitChunks(content, CHUNK_SIZE);
        int embeddingTokens = 0;
        if (!chunks.isEmpty()) {
            embeddingTokens = vectorStore.bulkIndexChunks(doc.getId(), chunks, filename, userId, doc.getVisibility(), null, orgTag);
        }

        doc.setStatus("ready");
        doc.setEmbeddingTokens(embeddingTokens > 0 ? embeddingTokens : null);
        documentMapper.updateById(doc);
        log.info("Document {} uploaded: {} chunks indexed, {} embedding tokens", doc.getId(), chunks.size(), embeddingTokens);
        return doc;
    }

    public List<Document> listByUser(Long userId) {
        return listByUser(userId, 1, Integer.MAX_VALUE);
    }

    public List<Document> listByUser(Long userId, int page, int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Document> p =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        return documentMapper.selectPage(p, new LambdaQueryWrapper<Document>()
            .eq(Document::getUserId, userId)
            .orderByDesc(Document::getCreatedAt)).getRecords();
    }

    public Document getById(Long id) {
        return documentMapper.selectById(id);
    }

    public long countByUser(Long userId) {
        return documentMapper.selectCount(new LambdaQueryWrapper<Document>()
            .eq(Document::getUserId, userId));
    }

    public void delete(Long docId, Long userId) {
        Document doc = documentMapper.selectById(docId);
        if (doc != null && doc.getUserId().equals(userId)) {
            vectorStore.deleteDoc(docId);
            documentMapper.deleteById(docId);
        }
    }

    /** Vector search via ES with orgTag-based access control */
    public List<String> retrieve(Long userId, String query, int topK) {
        String role = com.aicodehub.common.UserContext.getRole();
        String orgTags = com.aicodehub.common.UserContext.getOrgTags();
        boolean skipFilter = "admin".equalsIgnoreCase(role) || "test".equalsIgnoreCase(role);
        Set<String> effective = skipFilter ? Set.of() : orgTagService.getEffectiveTagNames(orgTags);
        List<Map<String, Object>> hits = vectorStore.search(userId, effective, query, topK, skipFilter);
        if (hits.isEmpty()) return List.of("未找到相关内容");

        return hits.stream().map(h -> {
            String filename = (String) h.get("filename");
            String content = (String) h.get("content");
            double score = (double) h.get("score");
            String snippet = content.length() > 300 ? content.substring(0, 300) + "..." : content;
            return String.format("> 📎 来源：《%s》\n%s", filename, snippet);
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
