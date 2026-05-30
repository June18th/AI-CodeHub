package com.aicodehub.service.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    public ToolRegistry(List<ToolDefinition> toolList) {
        toolList.forEach(t -> {
            tools.put(t.name(), t);
            log.info("Registered tool: {}", t.name());
        });
    }

    public ToolDefinition get(String name) { return tools.get(name); }

    public List<Map<String, Object>> toOpenAiFormat() {
        List<Map<String, Object>> result = new ArrayList<>();
        tools.forEach((name, tool) -> {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tool.name());
            func.put("description", tool.description());
            func.put("parameters", tool.parameters());
            def.put("function", func);
            result.add(def);
        });
        return result;
    }
}
