package com.aicodehub.service;

import com.aicodehub.entity.AgentTask;
import com.aicodehub.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskMapper mapper;
    private final AgentTaskExecutor executor;

    public AgentTask submit(Long userId, String prompt, String modelType) {
        AgentTask task = new AgentTask();
        task.setUserId(userId);
        task.setPrompt(prompt);
        task.setModelType(modelType != null ? modelType : "deepseek");
        task.setStatus("pending");
        mapper.insert(task);
        executor.execute(task);
        return task;
    }

    public List<AgentTask> listByUser(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<AgentTask>()
            .eq(AgentTask::getUserId, userId)
            .orderByDesc(AgentTask::getCreatedAt));
    }

    public AgentTask getById(Long id) {
        return mapper.selectById(id);
    }
}
