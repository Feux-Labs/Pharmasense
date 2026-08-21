package com.pharmasense.inventory.entity;

import com.pharmasense.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Product master data - the thing a pharmacy sells, independent of any
 * particular delivery of it. Quantity and expiry live one level down on
 * {@link InventoryBatchEntity}, because the same product routinely arrives
 * in several batches with different expiry dates (this is what makes FEFO -
 * first-expiry-first-out - possible).
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_items")
public class InventoryItemEntity extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "generic_name")
    private String genericName;

    private String category;

    @Column(name = "sku")
    private String sku;

    private String manufacturer;

    @Column(nullable = false)
    private String unit = "unit";

    @Column(name = "unit_selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitSellingPrice;

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription = false;

    /** Null falls back to the owning pharmacy's {@code lowStockThresholdDefault}. */
    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Column(name = "qr_code", unique = true)
    private String qrCode;

    @Column(nullable = false)
    private boolean active = true;
}
