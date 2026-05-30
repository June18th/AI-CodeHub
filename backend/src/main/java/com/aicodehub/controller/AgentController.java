package com.aicodehub.controller;

import com.aicodehub.common.SseEmitterHelper;
import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.entity.Message;
import com.aicodehub.service.ConversationService;
import com.aicodehub.service.tool.FunctionCallHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final FunctionCallHandler functionCallHandler;
    private final ConversationService conversationService;

    @GetMapping("/chat")
    public SseEmitter agentChat(@RequestParam String prompt,
                                @RequestParam(defaultValue = "deepseek") String modelType,
                                @RequestParam(required = false) Long conversationId) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(180_000L);
        SseSaveWrapper wrapper = new SseSaveWrapper(emitter);

        List<Map<String, String>> messages = new ArrayList<>();
        if (conversationId != null) {
            conversationService.saveMessage(conversationId, "user", prompt);
            List<Message> history = conversationService.getContext(conversationId);
            for (Message m : history) {
                messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", prompt));

        functionCallHandler.handleWithTools(modelType, messages,
            wrapper::send,                           // onChunk
            toolName -> wrapper.send("🔧 调用工具: " + toolName + "..."),  // onToolCall
            () -> wrapper.complete(() -> {           // onDone
                if (conversationId != null) {
                    String resp = wrapper.getResponse();
                    if (!resp.isEmpty()) {
                        conversationService.saveMessage(conversationId, "assistant", resp);
                        conversationService.updateTitle(conversationId, prompt);
                    }
                }
            }),
            errMsg -> {                             // onError
                wrapper.send(errMsg);
                wrapper.complete(() -> {});
            });

        return emitter;
    }
}
