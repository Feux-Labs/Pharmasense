package com.pharmasense.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.cloudinary")
public record CloudinaryProperties(
        String cloudName,
        String apiKey,
        String apiSecret) {
}
