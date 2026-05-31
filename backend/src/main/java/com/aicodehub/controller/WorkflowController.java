package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.SseEmitterHelper;
import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.common.UserContext;
import com.aicodehub.entity.Workflow;
import com.aicodehub.mapper.WorkflowMapper;
import com.aicodehub.service.workflow.WorkflowEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowMapper workflowMapper;
    private final WorkflowEngine engine;

    @PostMapping
    @PreAuthorize("hasRole('workflow:create')")
    public Result<?> create(@RequestBody Map<String, String> body) {
        Workflow wf = new Workflow();
        wf.setUserId(UserContext.getUserId());
        wf.setName(body.get("name"));
        wf.setDescription(body.getOrDefault("description", ""));
        wf.setDefinition(body.get("definition"));
        wf.setStatus("draft");
        workflowMapper.insert(wf);
        return Result.ok(Map.of("id", wf.getId(), "name", wf.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('workflow:create')")
    public Result<?> list() {
        return Result.ok(workflowMapper.selectList(new LambdaQueryWrapper<Workflow>()
            .eq(Workflow::getUserId, UserContext.getUserId())
            .orderByDesc(Workflow::getUpdatedAt)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('workflow:create')")
    public Result<?> get(@PathVariable Long id) {
        Workflow wf = workflowMapper.selectById(id);
        if (wf == null) return Result.fail(404, "工作流不存在");
        return Result.ok(Map.of("id", wf.getId(), "name", wf.getName(),
            "description", wf.getDescription(), "definition", wf.getDefinition()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('workflow:create')")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Workflow wf = workflowMapper.selectById(id);
        if (wf == null) return Result.fail(404, "工作流不存在");
        if (body.containsKey("name")) wf.setName(body.get("name"));
        if (body.containsKey("description")) wf.setDescription(body.get("description"));
        if (body.containsKey("definition")) wf.setDefinition(body.get("definition"));
        workflowMapper.updateById(wf);
        return Result.ok(Map.of("id", wf.getId(), "name", wf.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('workflow:create')")
    public Result<?> delete(@PathVariable Long id) {
        workflowMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/{id}/run")
    @PreAuthorize("hasRole('workflow:create')")
    public SseEmitter run(@PathVariable Long id,
                          @RequestParam(defaultValue = "{}") String params,
                          @RequestParam(required = false) String input) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(300_000L);
        Workflow wf = workflowMapper.selectById(id);
        if (wf == null) {
            SseEmitterHelper.send(emitter, "[ERROR] 工作流不存在");
            SseEmitterHelper.complete(emitter);
            return emitter;
        }
        SseSaveWrapper wrapper = new SseSaveWrapper(emitter);
        try {
            Map<String, String> paramMap = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(params, Map.class);
            if (input != null && !input.isEmpty()) {
                paramMap.put("user_input", input);
            }
            engine.execute(wf.getDefinition(), paramMap, wrapper);
        } catch (Exception e) {
            wrapper.send("[ERROR] " + e.getMessage());
        }
        wrapper.complete(() -> {});
        return emitter;
    }
}
