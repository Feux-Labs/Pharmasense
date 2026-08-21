package com.pharmasense.sync.entity;

import com.pharmasense.sync.enums.SyncEntityTypeEnum;
import com.pharmasense.sync.enums.SyncOperationEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only log every tenant-scoped write appends a row to (see
 * {@code SyncChangeRecorder}). {@code sequenceNumber} is a Postgres
 * {@code bigserial} - unlike the UUID id, it's monotonically increasing, so
 * it doubles as the cursor an offline client hands back on its next pull
 * ("send me everything after sequence N").
 */
@Getter
@Setter
@Entity
@Table(name = "sync_change_log")
public class SyncChangeLogEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "pharmacy_id", nullable = false, updatable = false)
    private UUID pharmacyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 32)
    private SyncEntityTypeEnum entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private SyncOperationEnum operation;

    /** JSON snapshot of the entity's response DTO at the time of this change; null for DELETE. */
    @Column(columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "sequence_number", insertable = false, updatable = false)
    private Long sequenceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
