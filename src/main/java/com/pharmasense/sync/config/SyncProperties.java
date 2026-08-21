package com.pharmasense.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pharmasense.sync")
public record SyncProperties(
        int tenantSnapshotCacheTtlHours,
        int maxPushBatchSize) {
}
