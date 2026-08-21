package com.pharmasense.sync.dto;

import com.pharmasense.inventory.enums.StockMovementTypeEnum;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * A stock count/adjustment made while offline, queued locally, and now being
 * replayed. {@code clientOperationId} is generated once on the device the
 * moment the user makes the change - it must stay the same across retries so
 * {@code SyncPushService} can detect and skip a duplicate submission.
 */
public record PendingStockAdjustmentDto(
        @NotNull UUID clientOperationId,
        @NotNull UUID batchId,
        @NotNull StockMovementTypeEnum movementType,
        int quantityDelta,
        String reason,
        Instant clientTimestamp) {
}
