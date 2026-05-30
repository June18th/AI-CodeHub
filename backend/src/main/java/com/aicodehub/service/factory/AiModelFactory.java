package com.aicodehub.service.factory;

import com.aicodehub.service.strategy.AiModelStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AiModelFactory {

    private final Map<String, AiModelStrategy> strategyMap = new ConcurrentHashMap<>();

    /** Spring injects all AiModelStrategy beans automatically */
    public AiModelFactory(List<AiModelStrategy> strategies) {
        strategies.forEach(s -> {
            strategyMap.put(s.getModelType(), s);
            log.info("Registered AI model strategy: {}", s.getModelType());
        });
    }

    public AiModelStrategy getStrategy(String modelType) {
        AiModelStrategy strategy = strategyMap.get(modelType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported model type: " + modelType);
        }
        return strategy;
    }
}
