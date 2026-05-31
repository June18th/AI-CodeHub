package com.aicodehub.service.agent;

import lombok.Getter;

@Getter
public class AgentBudget {
    private final int maxIterations;
    private final int maxTokens;
    private int iterationsUsed;
    private int tokensUsed;
    private boolean forcedExit;

    public AgentBudget(int maxIterations, int maxTokens) {
        this.maxIterations = maxIterations;
        this.maxTokens = maxTokens;
    }

    public void incrementIteration() {
        iterationsUsed++;
    }

    public void spendTokens(int tokens) {
        tokensUsed += tokens;
    }

    public boolean isExhausted() {
        if (iterationsUsed >= maxIterations) {
            forcedExit = true;
            return true;
        }
        if (tokensUsed >= maxTokens) {
            forcedExit = true;
            return true;
        }
        return false;
    }

    public String status() {
        return "轮次: " + iterationsUsed + "/" + maxIterations + ", Token: " + tokensUsed + "/" + maxTokens;
    }
}
