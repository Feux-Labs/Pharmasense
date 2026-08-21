package com.pharmasense.prescription.dto;

import java.util.UUID;

public record PrescriptionItemResponse(
        UUID id,
        UUID inventoryItemId,
        String itemName,
        int quantityPrescribed,
        int quantityFilled,
        String dosageInstructions) {
}
