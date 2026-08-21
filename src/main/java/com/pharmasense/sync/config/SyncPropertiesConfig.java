package com.pharmasense.sync.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SyncProperties.class)
public class SyncPropertiesConfig {
}
