package com.pharmasense.inventory.entity;

import com.pharmasense.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One physical delivery of an {@link InventoryItemEntity}: a batch/lot
 * number, the quantity remaining from it, and its expiry date. FEFO logic
 * (see {@code InventoryBatchService#pickBatchForSale}) always draws down the
 * batch with the nearest expiry date first.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_batches")
public class InventoryBatchEntity extends TenantScopedEntity {

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "unit_cost_price", precision = 12, scale = 2)
    private BigDecimal unitCostPrice;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;

    @Column(name = "qr_code", unique = true)
    private String qrCode;
}
