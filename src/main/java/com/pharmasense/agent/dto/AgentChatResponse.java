package com.pharmasense.agent.dto;

import java.util.List;

public record AgentChatResponse(
        String reply,
        List<AgentActionSummary> actionsPerformed) {
}
