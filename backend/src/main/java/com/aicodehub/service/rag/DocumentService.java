package com.aicodehub.service.rag;

import com.aicodehub.entity.Document;
import com.aicodehub.entity.DocumentChunk;
import com.aicodehub.mapper.DocumentChunkMapper;
import com.aicodehub.mapper.DocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;

    private static final int CHUNK_SIZE = 500;

    @Transactional
    public Document upload(Long userId, String filename, String fileType, String content) {
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFilename(filename);
        doc.setFileType(fileType);
        doc.setStatus("processing");
        documentMapper.insert(doc);

        List<String> chunks = splitChunks(content, CHUNK_SIZE);
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk c = new DocumentChunk();
            c.setDocumentId(doc.getId());
            c.setChunkIndex(i);
            c.setContent(chunks.get(i));
            chunkMapper.insert(c);
        }

        doc.setStatus("ready");
        documentMapper.updateById(doc);
        log.info("Document {} uploaded: {} chunks", doc.getId(), chunks.size());
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
            chunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, docId));
            documentMapper.deleteById(docId);
        }
    }

    /**
     * Retrieve top-K relevant chunks across all user docs.
     * Falls back to LIKE search if fulltext index not available.
     */
    public List<String> retrieve(Long userId, String query, int topK) {
        List<Document> docs = documentMapper.selectList(new LambdaQueryWrapper<Document>()
            .eq(Document::getUserId, userId).eq(Document::getStatus, "ready"));
        if (docs.isEmpty()) return List.of();

        List<String> results = new ArrayList<>();
        for (Document doc : docs) {
            try {
                List<DocumentChunk> chunks = chunkMapper.searchByFulltext(doc.getId(), query, 3);
                if (chunks.isEmpty()) {
                    chunks = chunkMapper.searchByLike(doc.getId(), query, 3);
                }
                for (DocumentChunk c : chunks) {
                    String snippet = c.getContent();
                    if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "...";
                    results.add("[" + doc.getFilename() + "] " + snippet);
                }
            } catch (Exception e) {
                // Fulltext may not be supported, fallback to LIKE
                List<DocumentChunk> chunks = chunkMapper.searchByLike(doc.getId(), query, 3);
                for (DocumentChunk c : chunks) {
                    String snippet = c.getContent();
                    if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "...";
                    results.add("[" + doc.getFilename() + "] " + snippet);
                }
            }
        }
        return results.stream().limit(topK).collect(Collectors.toList());
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
