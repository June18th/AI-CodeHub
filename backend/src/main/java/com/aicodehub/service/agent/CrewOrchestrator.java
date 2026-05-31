package com.aicodehub.service.agent;

import com.aicodehub.common.SseSaveWrapper;
import com.aicodehub.service.AiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrewOrchestrator {

    private final AgentWorker agentWorker;
    private final AiApiClient aiClient;

    /**
     * Execute a crew of agents on a user task.
     * Phase 1: Architect splits task into subtasks
     * Phase 2: Developers execute subtasks in parallel (round-robin)
     * Phase 3: Reviewer merges results
     */
    public void execute(String userPrompt, List<AgentRole> roles, SseSaveWrapper out) {
        out.send("🚀 启动多 Agent 协作...\n");

        // Phase 1: Architect splits the task
        AgentRole architect = roles.stream().filter(r -> "架构师".equals(r.name())).findFirst().orElse(null);
        AgentRole developer = roles.stream().filter(r -> "开发者".equals(r.name())).findFirst().orElse(null);
        AgentRole reviewer = roles.stream().filter(r -> "审查者".equals(r.name())).findFirst().orElse(null);

        if (architect == null && developer == null) {
            runSimpleParallel(userPrompt, 2, out);
            return;
        }

        out.send("[架构师] 正在分析任务...\n");
        agentWorker.execute("架构师", "你是任务分析专家，将复杂任务拆分为独立的子任务。只输出子任务列表，一行一个。",
            "deepseek", List.of(), userPrompt)
            .thenAccept(splitOutput -> {
                String[] tasks = splitOutput.trim().split("\n");
                out.send("📋 拆分为 " + tasks.length + " 个子任务\n");

                // Phase 2: Developers execute subtasks in parallel
                int devCount = developer != null ? developer.count() : 2;
                List<CompletableFuture<String>> futures = new ArrayList<>();
                for (int i = 0; i < tasks.length; i++) {
                    final int idx = i;
                    String devName = "开发者-" + ((idx % devCount) + 1);
                    String devPrompt = developer != null ? developer.systemPrompt()
                        : "你是专业开发者，高效准确地完成任务。";
                    out.send("⚙️ " + devName + " 执行: " + tasks[idx].trim() + "\n");
                    futures.add(agentWorker.execute(devName, devPrompt,
                        "deepseek", List.of(), tasks[idx].trim()));
                }

                // Phase 3: Wait for all and review
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(180, TimeUnit.SECONDS)
                    .thenRun(() -> {
                        List<String> results = futures.stream()
                            .map(f -> { try { return f.get(); } catch (Exception e) { return "执行失败"; } })
                            .collect(Collectors.toList());

                        if (reviewer != null) {
                            out.send("🔍 审查者正在汇总...\n");
                            String reviewPrompt = reviewer.systemPrompt()
                                + "\n\n原始任务: " + userPrompt + "\n\n子任务结果:\n"
                                + String.join("\n---\n", results);
                            agentWorker.execute("审查者", reviewPrompt, "deepseek", List.of(), "请汇总并输出最终答案")
                                .thenAccept(finalResult -> {
                                    out.send(finalResult);
                                    out.complete(() -> {});
                                });
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < results.size(); i++) {
                                sb.append("**").append(tasks[i].trim()).append("**\n").append(results.get(i)).append("\n\n");
                            }
                            out.send(sb.toString());
                            out.complete(() -> {});
                        }
                    });
            });
    }

    /** Simple parallel execution without role hierarchy — just split and run */
    private void runSimpleParallel(String prompt, int count, SseSaveWrapper out) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            futures.add(agentWorker.execute("Agent-" + (idx + 1),
                "高效准确地回答用户问题，用中文输出。",
                "deepseek", List.of(), prompt));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(120, TimeUnit.SECONDS)
            .thenRun(() -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < futures.size(); i++) {
                    try { sb.append(futures.get(i).get()).append("\n\n"); } catch (Exception ignored) {}
                }
                out.send(sb.toString().trim());
                out.complete(() -> {});
            });
    }
}
