package com.pharmasense.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.zeptomail")
public record ZeptoMailProperties(
        String apiUrl,
        String apiToken,
        String fromEmail,
        String fromName,
        boolean sendEnabled) {
}
