package com.pharmasense.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryBatchCreateRequest(
        @NotBlank String batchNumber,
        @Min(1) int quantityOnHand,
        @DecimalMin(value = "0", inclusive = true) BigDecimal unitCostPrice,
        LocalDate expiryDate,
        LocalDate receivedAt) {
}
