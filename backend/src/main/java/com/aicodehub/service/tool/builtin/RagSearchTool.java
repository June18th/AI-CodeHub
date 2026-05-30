package com.aicodehub.service.tool.builtin;

import com.aicodehub.common.UserContext;
import com.aicodehub.service.rag.DocumentService;
import com.aicodehub.service.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RagSearchTool implements ToolDefinition {

    private final DocumentService documentService;

    @Override
    public String name() { return "rag_search"; }

    @Override
    public String description() {
        return "在用户已上传的知识库文档中检索相关内容。当需要查找用户自己的文档资料时使用。输入检索关键词。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "检索关键词或问题");
        props.put("query", query);
        params.put("properties", props);
        params.put("required", List.of("query"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        Long userId = UserContext.getUserId();
        if (userId == null) return "需要登录才能使用知识库检索";
        List<String> results = documentService.retrieve(userId, query, 5);
        if (results.isEmpty()) return "未找到相关内容";
        return String.join("\n---\n", results);
    }
}
