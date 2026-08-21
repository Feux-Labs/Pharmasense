package com.pharmasense.agent.dto;

public record AgentActionSummary(
        String toolName,
        boolean success,
        String detail) {
}
