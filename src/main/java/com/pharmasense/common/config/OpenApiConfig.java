package com.pharmasense.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serves interactive API docs at /swagger-ui.html. The "bearerAuth" scheme
 * lets you paste an access token in the UI and call protected endpoints
 * directly, which is the fastest way to explore the API while learning it.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI pharmasenseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pharmasense API")
                        .description("Backend API for the Pharmasense pharmacy operating system")
                        .version("v1")
                        .contact(new Contact().name("Pharmasense").email("engineering@pharmasense.app")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
