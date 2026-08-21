package com.pharmasense.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is wide open by path but locked down by explicit origin allowlist -
 * the Next.js frontend origin(s) come from config so local/staging/prod can
 * differ without a code change.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${pharmasense.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Sync-Cursor")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
