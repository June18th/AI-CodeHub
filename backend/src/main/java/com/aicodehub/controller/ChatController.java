package com.aicodehub.controller;

import com.aicodehub.common.SseEmitterHelper;
import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.entity.Conversation;
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
    private final com.aicodehub.service.AuditService auditService;

    @GetMapping("/stream")
    public SseEmitter chatStream(@RequestParam String prompt,
                                 @RequestParam(defaultValue = "deepseek") String modelType,
                                 @RequestParam(required = false) Long conversationId,
                                 @RequestParam(required = false) Long userId) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(120_000L);

        if (conversationId != null) {
            Conversation conv = conversationService.getById(conversationId);
            if (conv == null && userId != null) {
                conv = conversationService.create(userId, modelType, prompt);
            }
            if (conv == null) {
                AiModelStrategy strategy = aiModelFactory.getStrategy(modelType);
                strategy.chatStream(prompt, emitter);
                return emitter;
            }

            final Long cid = conv.getId();
            List<Message> history = conversationService.getContext(cid);
            conversationService.saveMessage(cid, "user", prompt);

            List<Map<String, String>> messages = new ArrayList<>();
            for (Message m : history) {
                messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
            messages.add(Map.of("role", "user", "content", prompt));

            SseSaveWrapper wrapper = new SseSaveWrapper(emitter);
            final long start = System.currentTimeMillis();
            apiClient.streamCall(modelType, messages,
                wrapper::send,
                () -> wrapper.complete(() -> {
                    String response = wrapper.getResponse();
                    if (!response.isEmpty()) {
                        int in = wrapper.getInputTokens();
                        int out = wrapper.getOutputTokens();
                        conversationService.saveMessage(cid, "assistant", response,
                            in > 0 ? in : null, out > 0 ? out : null);
                        conversationService.updateTitle(cid, prompt);
                        auditService.log(userId, null, modelType, "/api/v1/chat/stream", in, out,
                            (int)(System.currentTimeMillis() - start), "success", null);
                    }
                }),
                errMsg -> {
                    conversationService.saveMessage(cid, "assistant", errMsg);
                    auditService.log(userId, null, modelType, "/api/v1/chat/stream", 0, 0,
                        (int)(System.currentTimeMillis() - start), "error", errMsg);
                    wrapper.send(errMsg);
                    wrapper.complete(() -> {});
                });
        } else {
            // ---- 游客模式 ----
            AiModelStrategy strategy = aiModelFactory.getStrategy(modelType);
            strategy.chatStream(prompt, emitter);
        }

        return emitter;
    }
}
