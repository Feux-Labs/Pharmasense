package com.pharmasense.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.admin")
public record AdminProperties(
        int impersonationTtlMinutes,
        String bootstrapEmail,
        String bootstrapFullName) {
}
