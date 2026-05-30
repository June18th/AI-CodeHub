package com.aicodehub.controller;

import com.aicodehub.common.SseEmitterHelper;
import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.entity.Message;
import com.aicodehub.service.AiApiClient;
import com.aicodehub.service.ConversationService;
import com.aicodehub.service.factory.AiModelFactory;
import com.aicodehub.service.strategy.AiModelStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AiModelFactory aiModelFactory;
    private final AiApiClient apiClient;
    private final ConversationService conversationService;

    @GetMapping("/stream")
    public SseEmitter chatStream(@RequestParam String prompt,
                                 @RequestParam(defaultValue = "deepseek") String modelType,
                                 @RequestParam(required = false) Long conversationId) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(120_000L);

        if (conversationId != null) {
            // ---- 会话模式：上下文 + 持久化 ----
            List<Message> history = conversationService.getContext(conversationId);
            conversationService.saveMessage(conversationId, "user", prompt);

            List<Map<String, String>> messages = new ArrayList<>();
            for (Message m : history) {
                messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
            messages.add(Map.of("role", "user", "content", prompt));

            SseSaveWrapper wrapper = new SseSaveWrapper(emitter);
            apiClient.streamCall(modelType, messages,
                wrapper::send,
                () -> wrapper.complete(() -> {
                    String response = wrapper.getResponse();
                    if (!response.isEmpty()) {
                        conversationService.saveMessage(conversationId, "assistant", response);
                        conversationService.updateTitle(conversationId, prompt);
                    }
                }),
                errMsg -> {
                    conversationService.saveMessage(conversationId, "assistant", errMsg);
                    wrapper.send(errMsg);
                    wrapper.complete(() -> {});
                });
        } else {
            // ---- 游客模式：单次对话 ----
            AiModelStrategy strategy = aiModelFactory.getStrategy(modelType);
            strategy.chatStream(prompt, emitter);
        }

        return emitter;
    }
}
