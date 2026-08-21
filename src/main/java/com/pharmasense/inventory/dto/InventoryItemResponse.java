package com.pharmasense.inventory.dto;

import com.pharmasense.inventory.enums.ExpiryStatusEnum;
import com.pharmasense.inventory.enums.StockLevelStatusEnum;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        String name,
        String genericName,
        String category,
        String sku,
        String manufacturer,
        String unit,
        BigDecimal unitSellingPrice,
        boolean requiresPrescription,
        Integer lowStockThreshold,
        boolean active,
        String qrCode,
        int totalQuantityOnHand,
        StockLevelStatusEnum stockLevelStatus,
        LocalDate nearestExpiryDate,
        ExpiryStatusEnum expiryStatus,
        Instant createdAt) {
}
