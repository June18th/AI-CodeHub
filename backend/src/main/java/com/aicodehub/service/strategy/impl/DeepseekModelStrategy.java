package com.aicodehub.service.strategy.impl;

import com.aicodehub.common.SseEmitterHelper;
import com.aicodehub.service.AiApiClient;
import com.aicodehub.service.strategy.AiModelStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeepseekModelStrategy implements AiModelStrategy {
    private final AiApiClient apiClient;
    @Override public String getModelType() { return "deepseek"; }

    @Override
    public void chatStream(String prompt, SseEmitter emitter) {
        apiClient.streamCall("deepseek", prompt,
            chunk -> SseEmitterHelper.send(emitter, chunk),
            () -> SseEmitterHelper.complete(emitter),
            errMsg -> { SseEmitterHelper.send(emitter, errMsg); SseEmitterHelper.complete(emitter); });
    }
    @Override
    public void chatStreamWithContext(List<Map<String, String>> messages, SseEmitter emitter) {
        apiClient.streamCall("deepseek", messages,
            chunk -> SseEmitterHelper.send(emitter, chunk),
            () -> SseEmitterHelper.complete(emitter),
            errMsg -> { SseEmitterHelper.send(emitter, errMsg); SseEmitterHelper.complete(emitter); });
    }
}
