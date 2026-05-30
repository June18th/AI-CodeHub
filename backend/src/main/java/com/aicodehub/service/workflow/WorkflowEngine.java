package com.aicodehub.service.workflow;

import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.service.AiApiClient;
import com.aicodehub.service.tool.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkflowEngine {

    private final AiApiClient apiClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record StepDef(String id, String type, String prompt, List<String> dependsOn) {}

    public WorkflowEngine(AiApiClient apiClient, ToolRegistry toolRegistry) {
        this.apiClient = apiClient;
        this.toolRegistry = toolRegistry;
    }

    public void execute(String definitionJson, Map<String, String> params, SseSaveWrapper wrapper) {
        try {
            List<StepDef> steps = mapper.readValue(definitionJson, new TypeReference<List<StepDef>>() {});
            // Also parse full step data for extra fields
            List<Map<String, Object>> fullSteps = mapper.readValue(definitionJson, new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Map<String, Object>> fullStepMap = fullSteps.stream().collect(Collectors.toMap(s -> (String)s.get("id"), s -> s));
            if (steps.isEmpty()) { wrapper.send("[ERROR] 工作流无节点"); return; }

            Map<String, Map<String, String>> state = new LinkedHashMap<>();
            Map<String, StepDef> stepMap = steps.stream().collect(Collectors.toMap(s -> s.id, s -> s));

            // Find start nodes and build successor map
            Set<String> allIds = steps.stream().map(s -> s.id).collect(Collectors.toSet());
            Map<String, List<String>> successors = new HashMap<>();
            Map<String, Integer> inDegree = new HashMap<>();
            for (StepDef s : steps) {
                successors.put(s.id, new ArrayList<>());
                inDegree.put(s.id, s.dependsOn.size());
            }
            for (StepDef s : steps) {
                for (String dep : s.dependsOn) {
                    successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.id);
                }
            }

            // Run nodes level by level (parallel-safe within same level)
            List<List<String>> levels = computeLevels(steps, successors, inDegree);
            for (List<String> level : levels) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String nodeId : level) {
                    StepDef step = stepMap.get(nodeId);
                    if (step == null) continue;
                    futures.add(CompletableFuture.runAsync(() -> {
                        executeNode(step, state, params, fullStepMap, wrapper);
                    }));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(180, TimeUnit.SECONDS);
            }

            if (!steps.isEmpty()) {
                wrapper.send("\n--- 工作流完成 ---");
                wrapper.send("FINAL:" + mapper.writeValueAsString(state));
            }
        } catch (Exception e) {
            log.error("Workflow failed", e);
            wrapper.send("[ERROR] 工作流执行失败: " + e.getMessage());
        }
    }

    private List<List<String>> computeLevels(List<StepDef> steps, Map<String, List<String>> successors,
                                              Map<String, Integer> inDegree) {
        Map<String, Integer> deg = new HashMap<>(inDegree);
        Queue<String> queue = new LinkedList<>();
        for (StepDef s : steps) if (deg.get(s.id) == 0) queue.add(s.id);

        List<List<String>> levels = new ArrayList<>();
        while (!queue.isEmpty()) {
            List<String> level = new ArrayList<>(queue);
            levels.add(level);
            queue.clear();
            for (String id : level) {
                for (String next : successors.getOrDefault(id, List.of())) {
                    deg.merge(next, -1, Integer::sum);
                    if (deg.get(next) == 0) queue.add(next);
                }
            }
        }
        return levels;
    }

    private void executeNode(StepDef step, Map<String, Map<String, String>> state,
                              Map<String, String> params, Map<String, Map<String, Object>> fullStepMap,
                              SseSaveWrapper wrapper) {
        try {
            wrapper.send("--- 步骤: " + step.id + " ---");
            String resolved = resolvePrompt(step.prompt, state, params);

            // Input node: just pass through user_input
            if ("input".equals(step.type)) {
                String val = params.getOrDefault("user_input", resolved);
                synchronized (state) { state.put(step.id, Map.of("output", val)); }
                wrapper.send(val);
                return;
            }

            // Output node: resolve outputParams + responseContent
            if ("output".equals(step.type)) {
                Map<String, Object> fs = fullStepMap.getOrDefault(step.id, Map.of());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> outParams = (List<Map<String, Object>>) fs.getOrDefault("outputParams", List.of());
                String respContent = (String) fs.getOrDefault("responseContent", "");

                Map<String, String> vars = new LinkedHashMap<>();
                if (!outParams.isEmpty()) {
                    for (Map<String, Object> p : outParams) {
                        String name = (String) p.get("name");
                        String pt = (String) p.getOrDefault("type", "output");
                        if ("reference".equals(pt)) {
                            String ref = (String) p.getOrDefault("ref", "");
                            int dot = ref.indexOf('.');
                            if (dot > 0) {
                                String nid = ref.substring(0, dot), pname = ref.substring(dot + 1);
                                Map<String, String> us = state.get(nid);
                                if (us != null) vars.put(name, us.getOrDefault(pname, ref));
                            }
                        } else if ("input".equals(pt)) {
                            vars.put(name, (String) p.getOrDefault("value", ""));
                        }
                    }
                }
                String result = !respContent.isEmpty() ? respContent : step.prompt;
                for (var e : vars.entrySet()) result = result.replace("{{" + e.getKey() + "}}", e.getValue());
                result = resolvePrompt(result, state, params);
                if (result.isEmpty()) { StringBuilder all = new StringBuilder(); state.forEach((k,v)->all.append(v.getOrDefault("output","")).append("\n")); result = all.toString().trim(); }
                synchronized (state) { state.put(step.id, Map.of("output", result)); }
                wrapper.send(result);
                return;
            }

            if ("tool".equals(step.type)) {
                var tool = toolRegistry.get(resolved.trim());
                String result = tool != null ? tool.execute(Map.of()) : "未知工具: " + resolved;
                synchronized (state) { state.put(step.id, Map.of("output", result)); }
                wrapper.send(result);
                return;
            }

            List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", resolved));
            StringBuilder result = new StringBuilder();
            CompletableFuture<Void> future = new CompletableFuture<>();
            apiClient.streamCall("deepseek", messages,
                chunk -> { result.append(chunk); wrapper.send(chunk); },
                () -> {
                    synchronized (state) { state.put(step.id, Map.of("output", result.toString())); }
                    future.complete(null);
                },
                errMsg -> {
                    synchronized (state) { state.put(step.id, Map.of("output", "[ERROR] " + errMsg)); }
                    wrapper.send("[ERROR] " + errMsg);
                    future.complete(null);
                });
            future.get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            synchronized (state) { state.put(step.id, Map.of("output", "[ERROR] " + e.getMessage())); }
            wrapper.send("[ERROR] " + step.id + ": " + e.getMessage());
        }
    }

    private String resolvePrompt(String template, Map<String, Map<String, String>> state,
                                  Map<String, String> params) {
        String result = template;
        for (var entry : state.entrySet()) {
            for (var oe : entry.getValue().entrySet()) {
                result = result.replace("{{" + entry.getKey() + "." + oe.getKey() + "}}", oe.getValue());
            }
            result = result.replace("{{" + entry.getKey() + ".output}}",
                entry.getValue().getOrDefault("output", ""));
        }
        if (params != null) {
            // Alias {{input}} to user_input value
            if (params.containsKey("user_input") && !params.containsKey("input")) {
                params.put("input", params.get("user_input"));
            }
            for (var p : params.entrySet()) {
                result = result.replace("{{" + p.getKey() + "}}", p.getValue());
            }
        }
        return result;
    }
}
