package com.aicodehub.service.tool.builtin;

import com.aicodehub.common.UserContext;
import com.aicodehub.service.rag.EmbeddingService;
import com.aicodehub.service.rag.VectorStoreService;
import com.aicodehub.service.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchMemoryTool implements ToolDefinition {

    private final EmbeddingService embeddingService;
    private final com.aicodehub.service.MemorySummarizer memorySummarizer;

    @Override
    public String name() { return "search_memory"; }

    @Override
    public String description() {
        return "检索之前保存的长期记忆。当用户问'我之前说过什么''记住过什么'"
            + "或需要回忆历史偏好、决策、背景信息时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "搜索关键词或问题，语义匹配已保存的记忆");
        props.put("query", query);
        Map<String, Object> topK = new LinkedHashMap<>();
        topK.put("type", "integer");
        topK.put("description", "返回条数，默认5");
        props.put("topK", topK);
        params.put("properties", props);
        params.put("required", List.of("query"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long userId = UserContext.getUserId();
        if (userId == null) return "请先登录后再搜索记忆";

        String query = (String) args.get("query");
        int k = args.get("topK") instanceof Integer i ? i : 5;

        List<String> results = memorySummarizer.retrieve(userId, query, k);
        if (results.isEmpty()) return "未找到相关记忆";

        StringBuilder sb = new StringBuilder("找到以下相关记忆：\n");
        for (int i = 0; i < results.size(); i++) {
            sb.append(i + 1).append(". ").append(results.get(i)).append("\n");
        }
        return sb.toString().trim();
    }
}
