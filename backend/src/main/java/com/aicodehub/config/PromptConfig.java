package com.aicodehub.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Getter
@Component
public class PromptConfig {

    private final Map<String, String> prompts = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        load("assistant");
        load("knowledge");
        load("workflow");
        load("agent");
        log.info("Loaded {} system prompts: {}", prompts.size(), prompts.keySet());
    }

    public String get(String name) {
        return prompts.getOrDefault(name, prompts.get("assistant"));
    }

    private void load(String name) {
        try {
            var resource = new ClassPathResource("prompts/" + name + ".md");
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            prompts.put(name, content.trim());
        } catch (Exception e) {
            log.warn("Failed to load prompt: {}", name, e);
        }
    }
}
