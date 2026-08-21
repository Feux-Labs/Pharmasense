package com.pharmasense.sync.service;

import com.pharmasense.sync.dto.PendingStockAdjustmentDto;
import com.pharmasense.sync.dto.SyncPushResultDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Replays offline-queued stock adjustments (the most common thing a
 * pharmacy does while its counter has no connection: counting and adjusting
 * stock). Each item is applied by {@link SyncStockAdjustmentApplier} in its
 * own transaction, so one conflicting item in a batch never blocks the rest
 * from applying - the client gets a per-item result and only needs to
 * resolve the ones that failed.
 *
 * <p>Scoped to stock adjustments for this release. Adding another offline-
 * writable entity means: add a {@code Pending*Dto} and a sibling applier
 * bean following the same receipt-then-apply-then-record shape (or, once
 * there are three or more entity types, extract a shared
 * {@code SyncEntityHandler} interface).
 */
@Service
public class SyncPushService {

    private final SyncStockAdjustmentApplier syncStockAdjustmentApplier;

    public SyncPushService(SyncStockAdjustmentApplier syncStockAdjustmentApplier) {
        this.syncStockAdjustmentApplier = syncStockAdjustmentApplier;
    }

    public List<SyncPushResultDto> applyStockAdjustments(UUID pharmacyId, UUID performedByUserId, List<PendingStockAdjustmentDto> adjustments) {
        return adjustments.stream()
                .map(adjustment -> syncStockAdjustmentApplier.apply(pharmacyId, performedByUserId, adjustment))
                .toList();
    }
}
