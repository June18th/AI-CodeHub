package com.aicodehub.service.tool.builtin;

import com.aicodehub.common.UserContext;
import com.aicodehub.entity.Document;
import com.aicodehub.mapper.DocumentMapper;
import com.aicodehub.service.tool.ToolDefinition;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class KnowledgeStatsTool implements ToolDefinition {

    private final DocumentMapper documentMapper;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override public String name() { return "knowledge_stats"; }

    @Override
    public String description() {
        return "获取当前用户知识库的统计信息：文档总数、文件类型分布、最近上传记录。用于了解知识库概况。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", Map.of());
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long userId = UserContext.getUserId();
        if (userId == null) return "需要登录才能查看知识库统计";

        List<Document> docs = documentMapper.selectList(
            new LambdaQueryWrapper<Document>().eq(Document::getUserId, userId));

        if (docs.isEmpty()) return "知识库为空，尚未上传任何文档";

        Map<String, Integer> typeCount = new LinkedHashMap<>();
        for (Document d : docs) {
            typeCount.merge(d.getFileType() != null ? d.getFileType() : "unknown", 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("知识库统计:\n");
        sb.append("- 文档总数: ").append(docs.size()).append("\n");
        sb.append("- 文件类型分布: ");
        typeCount.forEach((type, cnt) -> sb.append(type).append("(").append(cnt).append(") "));
        sb.append("\n- 最近上传:\n");

        docs.stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(5)
            .forEach(d -> sb.append("  - ").append(d.getFilename())
                .append(" (").append(d.getFileType()).append(") ")
                .append(d.getCreatedAt() != null ? d.getCreatedAt().format(FMT) : "")
                .append(" [").append(d.getStatus()).append("]\n"));

        return sb.toString();
    }
}
