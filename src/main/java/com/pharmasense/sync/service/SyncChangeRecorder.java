package com.pharmasense.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasense.sync.entity.SyncChangeLogEntity;
import com.pharmasense.sync.enums.SyncEntityTypeEnum;
import com.pharmasense.sync.enums.SyncOperationEnum;
import com.pharmasense.sync.repository.SyncChangeLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Called by every feature service right after it commits a tenant-scoped
 * write, so the change log stays a complete record of everything an offline
 * client might need to catch up on. Runs in the caller's existing
 * transaction (no {@code @Transactional} here) - if the surrounding save
 * rolls back, the change-log row rolls back with it, so the log can never
 * drift out of sync with the actual data.
 */
@Service
public class SyncChangeRecorder {

    private static final Logger log = LoggerFactory.getLogger(SyncChangeRecorder.class);

    private final SyncChangeLogRepository syncChangeLogRepository;
    private final ObjectMapper objectMapper;

    public SyncChangeRecorder(SyncChangeLogRepository syncChangeLogRepository, ObjectMapper objectMapper) {
        this.syncChangeLogRepository = syncChangeLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID pharmacyId, SyncEntityTypeEnum entityType, UUID entityId, SyncOperationEnum operation, Object responseDto) {
        SyncChangeLogEntity entry = new SyncChangeLogEntity();
        entry.setPharmacyId(pharmacyId);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOperation(operation);
        entry.setCreatedAt(Instant.now());
        entry.setPayload(serialize(responseDto));
        syncChangeLogRepository.save(entry);
    }

    private String serialize(Object responseDto) {
        if (responseDto == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(responseDto);
        } catch (Exception e) {
            log.error("Failed to serialize sync change payload for {}", responseDto.getClass(), e);
            return null;
        }
    }
}
