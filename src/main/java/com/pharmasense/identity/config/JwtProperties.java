package com.pharmasense.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        int accessTokenTtlMinutes,
        int refreshTokenTtlDays) {
}
