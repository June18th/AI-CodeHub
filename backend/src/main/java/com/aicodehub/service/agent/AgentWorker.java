package com.aicodehub.service.agent;

import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.service.tool.FunctionCallHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class AgentWorker {

    private static final String SYSTEM_GUARD = """
        你是 AI-CodeHub 多 Agent 协作系统中的一员。请遵守以下规则：
        1. 只做被分配的任务，不要越权
        2. 工具调用失败时报告错误，不要反复重试
        3. 用简洁的中文输出结果""";

    private final FunctionCallHandler functionCallHandler;

    public CompletableFuture<String> execute(String agentName, String systemPrompt, String modelType,
                                              List<String> toolNames, String task) {
        CompletableFuture<String> future = new CompletableFuture<>();
        SseEmitter privateEmitter = new SseEmitter(300_000L);
        SseSaveWrapper wrapper = new SseSaveWrapper(privateEmitter);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_GUARD + "\n\n" + systemPrompt));
        messages.add(Map.of("role", "user", "content", task));

        functionCallHandler.handleWithTools(modelType, messages,
            chunk -> {},
            toolName -> {},
            () -> future.complete(wrapper.getResponse()),
            err -> future.completeExceptionally(new RuntimeException(err))
        );
        return future;
    }
}
