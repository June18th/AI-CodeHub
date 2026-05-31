package com.aicodehub.service;

import com.aicodehub.entity.AgentTask;
import com.aicodehub.mapper.AgentTaskMapper;
import com.aicodehub.service.tool.FunctionCallHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskExecutor {

    private final AgentTaskMapper mapper;
    private final FunctionCallHandler functionCallHandler;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public void execute(AgentTask task) {
        pool.submit(() -> {
            task.setStatus("running");
            mapper.updateById(task);
            try {
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "user", "content", task.getPrompt()));
                StringBuilder result = new StringBuilder();
                functionCallHandler.handleWithTools(task.getModelType(), messages,
                    result::append,
                    tool -> {},
                    () -> {
                        task.setStatus("done");
                        task.setResult(result.toString());
                        mapper.updateById(task);
                    },
                    err -> {
                        task.setStatus("failed");
                        task.setErrorMsg(err);
                        mapper.updateById(task);
                    });
            } catch (Exception e) {
                task.setStatus("failed");
                task.setErrorMsg(e.getMessage());
                mapper.updateById(task);
            }
        });
    }
}
