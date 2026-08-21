package com.pharmasense.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AgentMessageDto(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank String content) {
}
