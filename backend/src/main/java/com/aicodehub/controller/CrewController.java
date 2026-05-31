package com.aicodehub.controller;

import com.aicodehub.common.SseEmitterHelper;
import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.service.agent.AgentRole;
import com.aicodehub.service.agent.CrewOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crew")
@RequiredArgsConstructor
public class CrewController {

    private final CrewOrchestrator orchestrator;

    @GetMapping("/chat")
    @PreAuthorize("hasRole('workflow:create')")
    public SseEmitter crewChat(@RequestParam String prompt,
                                @RequestParam(defaultValue = "deepseek") String modelType) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(300_000L);
        SseSaveWrapper out = new SseSaveWrapper(emitter);

        List<AgentRole> roles = List.of(
            new AgentRole("开发者", "你是专业开发者，高效准确地完成任务。用中文输出。",
                modelType, List.of("web_search"), 2)
        );

        orchestrator.execute(prompt, roles, out);
        return emitter;
    }

    @PostMapping("/custom")
    @PreAuthorize("hasRole('workflow:create')")
    public SseEmitter customCrew(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = SseEmitterHelper.createEmitter(300_000L);
        SseSaveWrapper out = new SseSaveWrapper(emitter);

        String prompt = (String) body.get("prompt");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rolesRaw = (List<Map<String, Object>>) body.get("roles");
        List<AgentRole> roles = rolesRaw.stream().map(r -> new AgentRole(
            (String) r.get("name"),
            (String) r.get("systemPrompt"),
            r.getOrDefault("modelType", "deepseek").toString(),
            (List<String>) r.getOrDefault("tools", List.of()),
            r.get("count") instanceof Integer c ? c : 1
        )).toList();

        orchestrator.execute(prompt, roles, out);
        return emitter;
    }
}
