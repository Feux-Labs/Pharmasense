package com.pharmasense.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.otp")
public record OtpProperties(
        int length,
        int ttlMinutes,
        int maxVerifyAttempts,
        int resendCooldownSeconds) {
}
