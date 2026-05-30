package com.aicodehub.service.strategy;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;

public interface AiModelStrategy {
    String getModelType();
    void chatStream(String prompt, SseEmitter emitter);
    void chatStreamWithContext(List<Map<String, String>> messages, SseEmitter emitter);
}
