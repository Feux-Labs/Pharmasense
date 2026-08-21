package com.pharmasense.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, OtpProperties.class})
public class IdentityPropertiesConfig {
}
