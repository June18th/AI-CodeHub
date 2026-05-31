package com.aicodehub.service;

import com.aicodehub.entity.Message;
import com.aicodehub.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MessageMapper messageMapper;
    private final MemorySummarizer summarizer;

    private static final Map<String, Integer> MODEL_CONTEXTS = Map.of(
        "deepseek-chat", 32_000, "deepseek", 32_000,
        "qwen-turbo", 8_000, "qwen", 8_000,
        "gpt-3.5-turbo", 4_000, "gpt", 4_000,
        "glm-4-flash", 128_000, "zhipu", 128_000
    );
    private static final double BUDGET_RATIO = 0.8;
    private static final int LONG_TERM_BUDGET = 600;
    private static final int COMPRESSION_THRESHOLD = 20;

    /** Approximate token count: ~1 token per 3 characters (handles both Chinese and English) */
    private static int approxTokens(String text) {
        return text == null ? 0 : text.length() / 3;
    }

    private static int approxTokens(Message m) {
        return approxTokens(m.getContent());
    }

    public int getBudget(String modelType) {
        return (int) (MODEL_CONTEXTS.getOrDefault(modelType, 8_000) * BUDGET_RATIO);
    }

    public record ShortTermResult(List<Message> messages, boolean needsCompression) {}

    /** Build context with token budget + compression */
    public List<Map<String, String>> buildContext(Long userId, Long conversationId,
                                                   String prompt, String modelType) {
        List<Map<String, String>> messages = new ArrayList<>();
        int budget = getBudget(modelType);

        // Long-term memory: vector search + keyword + time + source scoring
        List<MemoryEntry> candidates = summarizer.retrieveEntries(userId, prompt, 10);
        if (!candidates.isEmpty()) {
            candidates.sort((a, b) -> Double.compare(b.combinedScore(prompt), a.combinedScore(prompt)));
            List<MemoryEntry> top = candidates.size() > 3 ? candidates.subList(0, 3) : candidates;
            StringBuilder ctx = new StringBuilder("以下是历史关键信息（仅供参考）：\n");
            for (MemoryEntry e : top) {
                String tag = "agent".equals(e.source()) ? "[主动记忆]" : "[历史摘要]";
                ctx.append("- ").append(tag).append(" ").append(e.content()).append("\n");
            }
            String sysMsg = ctx.toString();
            messages.add(Map.of("role", "system", "content", sysMsg));
            budget -= approxTokens(sysMsg);
        }

        // Short-term messages
        List<Message> all = messageMapper.selectList(new LambdaQueryWrapper<Message>()
            .eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getCreatedAt));
        int available = budget - approxTokens(prompt);
        List<Message> selected = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && available > 0; i--) {
            Message m = all.get(i);
            int t = approxTokens(m);
            if (t <= available) { selected.add(m); available -= t; }
        }
        Collections.reverse(selected);
        for (Message m : selected) messages.add(Map.of("role", m.getRole(), "content", m.getContent()));

        messages.add(Map.of("role", "user", "content", prompt));

        // Apply compression if history exceeds 60% of token budget
        int historyTokens = messages.stream().filter(m -> !"system".equals(m.get("role")) && !m.get("content").equals(prompt)).mapToInt(m -> approxTokens(m.get("content"))).sum();
        if (historyTokens > budget * 0.8) {
            messages = summarizer.compressHistory(messages);
        }
        return messages;
    }

    public boolean needsCompression(Long conversationId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
            .eq(Message::getConversationId, conversationId));
        return count >= COMPRESSION_THRESHOLD;
    }

    /** Async compress and store long-term memory after conversation */
    public void maybeCompress(Long userId, Long conversationId) {
        List<Message> recent = messageMapper.selectList(new LambdaQueryWrapper<Message>()
            .eq(Message::getConversationId, conversationId)
            .orderByDesc(Message::getCreatedAt)
            .last("LIMIT " + COMPRESSION_THRESHOLD));
        if (recent.size() >= COMPRESSION_THRESHOLD) {
            var ordered = new ArrayList<>(recent);
            java.util.Collections.reverse(ordered);
            summarizer.compressAndStore(userId, conversationId, ordered);
        }
    }
}
