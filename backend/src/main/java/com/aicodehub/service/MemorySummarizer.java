package com.aicodehub.service;

import com.aicodehub.entity.Message;
import com.aicodehub.service.rag.EmbeddingService;
import com.aicodehub.service.rag.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySummarizer {

    private final AiApiClient aiClient;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;

    private static final int MAP_CHUNK_SIZE = 10;
    private static final int KEEP_LAST_USER_TURNS = 3;
    private static final int COMPRESSION_THRESHOLD = 20;

    /**
     * Map-Reduce: split old messages into chunks, summarize each, merge into final summary.
     * Writes the compressed result as short-term memory (ES summaries index).
     */
    public void compressAndStore(Long userId, Long conversationId, List<Message> recentMessages) {
        if (recentMessages.size() < COMPRESSION_THRESHOLD) return;

        // Phase 1: Map — split into chunks of MAP_CHUNK_SIZE
        List<List<Message>> chunks = new ArrayList<>();
        for (int i = 0; i < recentMessages.size(); i += MAP_CHUNK_SIZE) {
            int end = Math.min(i + MAP_CHUNK_SIZE, recentMessages.size());
            chunks.add(recentMessages.subList(i, end));
        }
        if (chunks.size() <= 1) return; // nothing to reduce

        List<String> chunkSummaries = new ArrayList<>();
        compressChunks(chunks, 0, chunkSummaries, () -> {
            // Phase 2: Reduce — merge chunk summaries into one
            String combined = String.join("\n", chunkSummaries);
            aiClient.streamCall("deepseek",
                "将以下多段摘要合并为3-5条精炼要点（每条≤50字）：\n" + combined,
                new StringBuilder()::append,
                () -> {
                    float[] vec = embeddingService.embed(combined);
                    if (vec != null) vectorStore.indexSummary(userId, conversationId, combined, vec);
                    log.info("Map-Reduce compressed {} messages into {} chunks → 1 summary",
                        recentMessages.size(), chunks.size());
                },
                err -> log.error("Reduce phase failed: {}", err)
            );
        });
    }

    private void compressChunks(List<List<Message>> chunks, int idx,
                                 List<String> results, Runnable onDone) {
        if (idx >= chunks.size()) { onDone.run(); return; }
        String dialog = chunks.get(idx).stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));
        aiClient.streamCall("deepseek",
            "压缩以下对话为1段摘要（≤100字）：\n" + dialog,
            new StringBuilder()::append,
            () -> {
                String s = new StringBuilder().toString().trim();
                if (!s.isEmpty()) results.add(s);
                compressChunks(chunks, idx + 1, results, onDone);
            },
            err -> {
                log.error("Map phase chunk {} failed: {}", idx, err);
                compressChunks(chunks, idx + 1, results, onDone);
            }
        );
    }

    /**
     * ConversationHistory compression: compress messages before last K user turns.
     * Preserves tool_call/tool_result pairs by never cutting between them.
     *
     * Result: [system, user("summary"), assistant("ack"), ...last K user turns...]
     */
    public List<Map<String, String>> compressHistory(List<Map<String, String>> messages) {
        if (messages.size() < COMPRESSION_THRESHOLD) return messages;

        // Find boundary: index of the K-th last user message (from the end)
        int boundary = findUserBoundary(messages);
        if (boundary <= 1) return messages; // nothing to compress

        // Part to compress: messages[1..boundary) (skip system at index 0)
        List<Map<String, String>> toCompress = messages.subList(1, boundary);
        List<Map<String, String>> tail = messages.subList(boundary, messages.size());

        String dialog = toCompress.stream()
            .map(m -> m.get("role") + ": " + m.get("content"))
            .collect(Collectors.joining("\n"));

        StringBuilder compressed = new StringBuilder();
        aiClient.streamCall("deepseek",
            "总结以下历史对话为一段摘要（≤200字），用于后续对话的上下文。只输出摘要内容：\n" + dialog,
            compressed::append,
            () -> {
                String summary = "[已压缩的历史对话摘要]\n" + compressed.toString().trim();
                List<Map<String, String>> result = new ArrayList<>();
                result.add(messages.get(0)); // system
                result.add(Map.of("role", "user", "content", summary));
                result.add(Map.of("role", "assistant",
                    "content", "好的，我已了解之前的上下文，请继续。"));
                result.addAll(tail);
                messages.clear();
                messages.addAll(result);
                log.info("Compressed history: {}→{} messages (boundary at {})",
                    toCompress.size(), result.size(), boundary);
            },
            err -> log.error("History compression failed: {}", err)
        );
        return messages;
    }

    /**
     * Find the index of the K-th last user message, ensuring we don't cut
     * between tool_call and tool_result pairs.
     */
    private int findUserBoundary(List<Map<String, String>> messages) {
        int userCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            String role = messages.get(i).get("role");
            if ("user".equals(role)) {
                userCount++;
                if (userCount >= KEEP_LAST_USER_TURNS) return i;
            }
        }
        return 0; // compress everything if not enough user turns
    }

    /** Retrieve relevant long-term memories as scored entries */
    public List<MemoryEntry> retrieveEntries(Long userId, String query, int topK) {
        float[] qVec = embeddingService.embed(query);
        if (qVec == null) return List.of();
        return vectorStore.searchSummaries(userId, qVec, topK);
    }

    /** Convenience: retrieve content strings only (for tools) */
    public List<String> retrieve(Long userId, String query, int topK) {
        return retrieveEntries(userId, query, topK).stream()
            .sorted((a, b) -> Double.compare(b.combinedScore(query), a.combinedScore(query)))
            .limit(Math.min(topK, 5))
            .map(MemoryEntry::content)
            .toList();
    }
}
