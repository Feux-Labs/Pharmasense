package com.pharmasense.catalog.dto;

import com.pharmasense.inventory.dto.InventoryBatchResponse;

import java.math.BigDecimal;
import java.util.UUID;

/** Result of scanning a single batch sticker - that batch plus enough product context to display it standalone. */
public record CatalogBatchScanResponse(
        InventoryBatchResponse batch,
        UUID inventoryItemId,
        String itemName,
        String itemCategory,
        BigDecimal unitSellingPrice) {
}
