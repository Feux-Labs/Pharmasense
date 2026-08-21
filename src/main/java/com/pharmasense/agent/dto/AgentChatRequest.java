package com.pharmasense.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * {@code history} is optional prior turns of this conversation, sent back by
 * the client each time - there's no server-side conversation storage in this
 * release, so the frontend owns keeping the thread around (e.g. in local
 * state or IndexedDB alongside the rest of its offline-cached data).
 */
public record AgentChatRequest(
        @NotBlank String message,
        @Valid List<AgentMessageDto> history) {
}
