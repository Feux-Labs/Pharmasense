package com.pharmasense.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.sync.enums.SyncEntityTypeEnum;
import com.pharmasense.sync.enums.SyncOperationEnum;

import java.time.Instant;
import java.util.UUID;

public record SyncChangeEntryDto(
        long sequenceNumber,
        SyncEntityTypeEnum entityType,
        UUID entityId,
        SyncOperationEnum operation,
        JsonNode payload,
        Instant createdAt) {
}
