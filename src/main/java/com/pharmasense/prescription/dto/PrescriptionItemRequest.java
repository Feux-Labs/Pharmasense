package com.pharmasense.prescription.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PrescriptionItemRequest(
        @NotNull UUID inventoryItemId,
        @Min(1) int quantityPrescribed,
        String dosageInstructions) {
}
