package com.pharmasense.agent.client;

import java.util.List;

public record GroqMessageResponse(
        String finishReason,
        List<GroqContentBlock> content) {

    public boolean requestedToolUse() {
        return "tool_calls".equals(finishReason);
    }

    public String concatenatedText() {
        return content.stream()
                .filter(GroqContentBlock.Text.class::isInstance)
                .map(block -> ((GroqContentBlock.Text) block).text())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}
