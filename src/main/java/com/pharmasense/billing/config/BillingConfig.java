package com.pharmasense.billing.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({PaystackProperties.class, BillingProperties.class})
public class BillingConfig {

    @Bean
    public WebClient paystackWebClient(WebClient.Builder builder, PaystackProperties properties) {
        return builder
                .baseUrl(properties.apiUrl())
                .defaultHeader("Authorization", "Bearer " + properties.secretKey())
                .build();
    }
}
