package com.pharmasense.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.paystack")
public record PaystackProperties(
        String apiUrl,
        String secretKey,
        long basicPriceKobo,
        long proPriceKobo,
        String callbackUrl) {
}
