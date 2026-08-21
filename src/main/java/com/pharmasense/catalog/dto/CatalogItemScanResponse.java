package com.pharmasense.catalog.dto;

import com.pharmasense.inventory.dto.InventoryBatchResponse;
import com.pharmasense.inventory.dto.InventoryItemResponse;

import java.util.List;

/** Result of scanning a product-group label - the item's aggregate status plus every batch behind it. */
public record CatalogItemScanResponse(
        InventoryItemResponse item,
        List<InventoryBatchResponse> batches) {
}
