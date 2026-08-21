package com.pharmasense.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    private static final String GROQ_API_BASE_URL = "https://api.groq.com";

    @Bean
    public WebClient groqWebClient(WebClient.Builder builder, AgentProperties properties) {
        return builder
                .baseUrl(GROQ_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }
}
