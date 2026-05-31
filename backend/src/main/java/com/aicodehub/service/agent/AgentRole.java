package com.aicodehub.service.agent;

import java.util.List;

public record AgentRole(
    String name,
    String systemPrompt,
    String modelType,
    List<String> tools,
    int count
) {}
