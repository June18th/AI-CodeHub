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
public class SaveMemoryTool implements ToolDefinition {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;

    @Override
    public String name() { return "save_memory"; }

    @Override
    public String description() {
        return "将一条值得长期保存的信息存入记忆。当用户明确说'记住''以后''别忘了'，"
            + "或对话中出现稳定的偏好、事实、决策时主动调用。例如："
            + "'记住我用 Maven 不用 Gradle'、'以后默认用千问模型'、"
            + "用户透露了项目背景、技术栈偏好等长期有效的信息。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "string");
        content.put("description", "需要记住的内容，简洁描述关键事实");
        props.put("content", content);
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("description", "分类标签，如 preference / fact / decision / background");
        props.put("category", category);
        params.put("properties", props);
        params.put("required", List.of("content"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String content = (String) args.get("content");
        Long userId = UserContext.getUserId();
        if (userId == null) return "请先登录后再保存记忆";

        float[] vec = embeddingService.embed(content);
        if (vec == null) return "记忆保存失败：向量化出错";

        vectorStore.indexSummary(userId, null, content, vec, "agent");
        log.info("Agent saved memory for user {}: {}", userId, content);
        return "已记住：" + content;
    }
}
