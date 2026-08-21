package com.pharmasense.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasense.sync.config.SyncProperties;
import com.pharmasense.sync.dto.SyncChangeEntryDto;
import com.pharmasense.sync.dto.SyncPullResponse;
import com.pharmasense.sync.entity.SyncChangeLogEntity;
import com.pharmasense.sync.repository.SyncChangeLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Cheap incremental catch-up: "give me everything that changed after
 * sequence N". The client stores {@code nextCursor} and passes it back as
 * {@code since} on the following call; {@code hasMore} tells it whether to
 * immediately request another page rather than waiting for the next sync
 * interval (a device that's been offline for a week may have thousands of
 * changes to catch up on).
 */
@Service
public class SyncPullService {

    private final SyncChangeLogRepository syncChangeLogRepository;
    private final SyncProperties syncProperties;
    private final ObjectMapper objectMapper;

    public SyncPullService(SyncChangeLogRepository syncChangeLogRepository, SyncProperties syncProperties, ObjectMapper objectMapper) {
        this.syncChangeLogRepository = syncChangeLogRepository;
        this.syncProperties = syncProperties;
        this.objectMapper = objectMapper;
    }

    public SyncPullResponse pull(UUID pharmacyId, long since) {
        int pageSize = syncProperties.maxPushBatchSize() > 0 ? syncProperties.maxPushBatchSize() : 500;

        List<SyncChangeLogEntity> page = syncChangeLogRepository.findByPharmacyIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                pharmacyId, since, PageRequest.of(0, pageSize + 1));

        boolean hasMore = page.size() > pageSize;
        List<SyncChangeLogEntity> pageToReturn = hasMore ? page.subList(0, pageSize) : page;

        List<SyncChangeEntryDto> changes = pageToReturn.stream()
                .map(entry -> new SyncChangeEntryDto(
                        entry.getSequenceNumber(),
                        entry.getEntityType(),
                        entry.getEntityId(),
                        entry.getOperation(),
                        parsePayload(entry.getPayload()),
                        entry.getCreatedAt()))
                .toList();

        long nextCursor = pageToReturn.isEmpty() ? since : pageToReturn.get(pageToReturn.size() - 1).getSequenceNumber();
        return new SyncPullResponse(changes, nextCursor, hasMore);
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            return null;
        }
    }
}
