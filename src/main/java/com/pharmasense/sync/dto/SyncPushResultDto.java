package com.pharmasense.sync.dto;

import com.pharmasense.sync.enums.SyncPushResultStatusEnum;

import java.util.UUID;

public record SyncPushResultDto(
        UUID clientOperationId,
        SyncPushResultStatusEnum status,
        String message) {
}
