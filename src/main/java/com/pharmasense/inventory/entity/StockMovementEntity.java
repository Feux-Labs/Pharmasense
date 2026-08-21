package com.pharmasense.inventory.entity;

import com.pharmasense.common.domain.TenantScopedEntity;
import com.pharmasense.inventory.enums.StockMovementTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Immutable audit trail of every quantity change on a batch. This is what
 * the agentic AI's "check balance" tool and the analytics dashboards read
 * from, and it's also what {@code sync} change-recording piggybacks on for
 * inventory entities.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_movements")
public class StockMovementEntity extends TenantScopedEntity {

    @Column(name = "inventory_batch_id", nullable = false)
    private UUID inventoryBatchId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private StockMovementTypeEnum movementType;

    /** Positive for additions (received, returned), negative for reductions (sold, disposed, or a downward adjustment). */
    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    private String reason;

    @Column(name = "performed_by_user_id", nullable = false)
    private UUID performedByUserId;
}
