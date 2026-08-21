package com.pharmasense.sync.entity;

import com.pharmasense.sync.enums.SyncPushResultStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Records every client-generated {@code clientOperationId} a push batch has
 * ever applied. A flaky connection means the same offline mutation can be
 * submitted more than once - checking this table first is what makes push
 * safe to retry without double-applying a stock adjustment.
 */
@Getter
@Setter
@Entity
@Table(name = "sync_push_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_push_receipts_pharmacy_client_op", columnNames = {"pharmacy_id", "client_operation_id"})
})
public class SyncPushReceiptEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "pharmacy_id", nullable = false, updatable = false)
    private UUID pharmacyId;

    @Column(name = "client_operation_id", nullable = false, updatable = false)
    private UUID clientOperationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SyncPushResultStatusEnum status;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;
}
