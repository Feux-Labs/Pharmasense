package com.pharmasense.inventory.dto;

import com.pharmasense.inventory.enums.StockMovementTypeEnum;
import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(
        @NotNull StockMovementTypeEnum movementType,
        int quantityDelta,
        String reason) {
}
