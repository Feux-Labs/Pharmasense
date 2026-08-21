package com.pharmasense.inventory.dto;

import com.pharmasense.inventory.enums.ExpiryStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryBatchResponse(
        UUID id,
        UUID inventoryItemId,
        String batchNumber,
        int quantityOnHand,
        BigDecimal unitCostPrice,
        LocalDate expiryDate,
        LocalDate receivedAt,
        String qrCode,
        ExpiryStatusEnum expiryStatus) {
}
