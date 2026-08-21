package com.pharmasense.inventory.dto;

import com.pharmasense.inventory.enums.ExpiryStatusEnum;

import java.time.LocalDate;
import java.util.UUID;

/** One row on the expiry tracker screen - {@code daysRemaining} is the figure the UI leads with, per the design spec. */
public record ExpiryTrackerEntryResponse(
        UUID batchId,
        UUID inventoryItemId,
        String itemName,
        String batchNumber,
        int quantityOnHand,
        LocalDate expiryDate,
        long daysRemaining,
        ExpiryStatusEnum expiryStatus) {
}
