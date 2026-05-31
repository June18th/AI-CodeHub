package com.aicodehub.service;

/**
 * A single memory entry with metadata for multi-dimensional relevance scoring.
 */
public record MemoryEntry(
    String content,
    long savedAtMs,       // epoch millis when saved
    String source         // "agent" = explicitly saved, "auto" = compressed summary
) {
    /** Source weight: agent-saved memories are information-dense, weighted higher */
    public double sourceWeight() {
        return "agent".equals(source) ? 1.2 : 1.0;
    }

    /** Time decay: 1.0 at 0h, 0.5 at 24h, 0.1 at 72h+ */
    public double timeScore() {
        double hours = (System.currentTimeMillis() - savedAtMs) / 3_600_000.0;
        if (hours <= 24) return 1.0 - (hours / 24) * 0.5;        // 1.0 → 0.5
        if (hours <= 72) return 0.5 - ((hours - 24) / 48) * 0.4; // 0.5 → 0.1
        return 0.1;
    }

    /** Keyword match score: tokenize query, count word hits in content */
    public double keywordScore(String query) {
        if (query == null || query.isBlank()) return 0;
        String lower = content.toLowerCase();
        String[] words = query.toLowerCase().split("[\\s,，。.!！?？]+");
        int hits = 0;
        for (String w : words) {
            if (w.length() >= 2 && lower.contains(w)) hits++;
        }
        return words.length == 0 ? 0 : (double) hits / words.length;
    }

    /** Combined score: keyword(0.4) + time(0.3) + source(0.3) */
    public double combinedScore(String query) {
        return keywordScore(query) * 0.4 + timeScore() * 0.3 + sourceWeight() * 0.3;
    }
}
