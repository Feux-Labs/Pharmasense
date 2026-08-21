package com.pharmasense.prescription.entity;

import com.pharmasense.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "prescription_items")
public class PrescriptionItemEntity extends TenantScopedEntity {

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(name = "quantity_prescribed", nullable = false)
    private int quantityPrescribed;

    @Column(name = "quantity_filled", nullable = false)
    private int quantityFilled = 0;

    @Column(name = "dosage_instructions")
    private String dosageInstructions;
}
