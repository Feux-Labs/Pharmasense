package com.pharmasense.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryItemCreateRequest(
        @NotBlank String name,
        String genericName,
        String category,
        String sku,
        String manufacturer,
        String unit,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal unitSellingPrice,
        boolean requiresPrescription,
        @Min(0) Integer lowStockThreshold) {
}
