package com.aicodehub.service.tool;

import java.util.Map;

public interface ToolDefinition {
    String name();
    String description();
    Map<String, Object> parameters();
    String execute(Map<String, Object> args);
}
