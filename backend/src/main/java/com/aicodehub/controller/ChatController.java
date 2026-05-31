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
    private final com.aicodehub.service.MemoryService memoryService;
    private final com.aicodehub.service.AuditService auditService;
    private final com.aicodehub.common.JwtUtil jwtUtil;
    private final com.aicodehub.service.SessionService sessionService;

    private Long resolveUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                if (jwtUtil.validate(token)) {
                    Long uid = jwtUtil.getUserId(token);
                    try { sessionService.extend(uid); } catch (Exception ignored) {}
                    return uid;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    @GetMapping("/stream")
    public SseEmitter chatStream(@RequestParam String prompt,
                                 @RequestParam(defaultValue = "deepseek") String modelType,
                                 @RequestParam(required = false) Long conversationId,
                                 @RequestHeader(value = "Authorization", required = false) String auth) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(120_000L);
        final Long userId = resolveUserId(auth);

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
            conversationService.saveMessage(cid, "user", prompt);

            List<Map<String, String>> messages = memoryService.buildContext(userId, cid, prompt, modelType);

            SseSaveWrapper wrapper = new SseSaveWrapper(emitter);
            final long start = System.nanoTime();
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
                            (int)((System.nanoTime() - start) / 1_000_000), "success", null);
                    }
                    if (userId != null) memoryService.maybeCompress(userId, cid);
                }),
                errMsg -> {
                    conversationService.saveMessage(cid, "assistant", errMsg);
                    auditService.log(userId, null, modelType, "/api/v1/chat/stream", 0, 0,
                        (int)((System.nanoTime() - start) / 1_000_000), "error", errMsg);
                    wrapper.send(errMsg);
                    wrapper.complete(() -> {});
                });
        } else {
            // Guest mode — stream directly, still log audit
            final long start = System.nanoTime();
            SseSaveWrapper wrapper = new SseSaveWrapper(emitter);
            apiClient.streamCall(modelType, prompt,
                wrapper::send,
                () -> wrapper.complete(() -> {
                    auditService.log(null, null, modelType, "/api/v1/chat/stream",
                        wrapper.getInputTokens(), wrapper.getOutputTokens(),
                        (int)((System.nanoTime() - start) / 1_000_000), "success", null);
                }),
                errMsg -> {
                    auditService.log(null, null, modelType, "/api/v1/chat/stream", 0, 0,
                        (int)((System.nanoTime() - start) / 1_000_000), "error", errMsg);
                });
        }

        return emitter;
    }
}
