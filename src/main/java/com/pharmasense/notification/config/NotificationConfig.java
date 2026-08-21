package com.pharmasense.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ZeptoMailProperties.class)
public class NotificationConfig {

    @Bean
    public WebClient zeptoMailWebClient(WebClient.Builder builder, ZeptoMailProperties properties) {
        return builder
                .baseUrl(properties.apiUrl())
                .defaultHeader("Authorization", "Zoho-enczapikey " + properties.apiToken())
                .build();
    }
}
