package com.pharmasense.agent.client;

import com.fasterxml.jackson.databind.JsonNode;

/** One piece of a Groq (OpenAI-compatible) assistant turn - prose or a request to call a tool. */
public sealed interface GroqContentBlock permits GroqContentBlock.Text, GroqContentBlock.ToolCall {

    record Text(String text) implements GroqContentBlock {
    }

    record ToolCall(String id, String name, JsonNode arguments) implements GroqContentBlock {
    }
}
