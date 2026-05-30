package com.aicodehub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai")
public class AiModelProperties {
    private Map<String, ModelConfig> models;

    @Getter
    @Setter
    public static class ModelConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
    }

    public ModelConfig getConfig(String type) {
        return models != null ? models.get(type) : null;
    }

    public boolean hasKey(String type) {
        ModelConfig c = getConfig(type);
        return c != null && c.getApiKey() != null && !c.getApiKey().isBlank();
    }
}
