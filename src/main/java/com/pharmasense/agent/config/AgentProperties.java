package com.pharmasense.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.agent")
public record AgentProperties(
        String provider,
        String apiKey,
        String model,
        int maxOutputTokens,
        int maxToolIterations) {
}
