package com.pharmasense.sync.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.inventory.service.InventoryBatchService;
import com.pharmasense.sync.dto.PendingStockAdjustmentDto;
import com.pharmasense.sync.dto.SyncPushResultDto;
import com.pharmasense.sync.entity.SyncPushReceiptEntity;
import com.pharmasense.sync.enums.SyncPushResultStatusEnum;
import com.pharmasense.sync.repository.SyncPushReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * A separate bean (not just a method on {@link SyncPushService}) so
 * {@code @Transactional} actually takes effect: Spring's proxy-based AOP
 * can't intercept a method calling itself via {@code this} within the same
 * class, and {@link SyncPushService} needs to invoke this once per item in a
 * batch, each in its own transaction.
 */
@Service
public class SyncStockAdjustmentApplier {

    private static final Logger log = LoggerFactory.getLogger(SyncStockAdjustmentApplier.class);

    private final SyncPushReceiptRepository syncPushReceiptRepository;
    private final InventoryBatchService inventoryBatchService;

    public SyncStockAdjustmentApplier(SyncPushReceiptRepository syncPushReceiptRepository, InventoryBatchService inventoryBatchService) {
        this.syncPushReceiptRepository = syncPushReceiptRepository;
        this.inventoryBatchService = inventoryBatchService;
    }

    @Transactional
    public SyncPushResultDto apply(UUID pharmacyId, UUID performedByUserId, PendingStockAdjustmentDto adjustment) {
        var existingReceipt = syncPushReceiptRepository.findByPharmacyIdAndClientOperationId(pharmacyId, adjustment.clientOperationId());
        if (existingReceipt.isPresent()) {
            return new SyncPushResultDto(adjustment.clientOperationId(), SyncPushResultStatusEnum.ALREADY_APPLIED,
                    "This change was already applied in an earlier sync");
        }

        try {
            // adjustQuantity already appends a sync-change-log row internally (see InventoryBatchService.recordSyncChange)
            inventoryBatchService.adjustQuantity(
                    pharmacyId, adjustment.batchId(), adjustment.movementType(), adjustment.quantityDelta(),
                    adjustment.reason(), performedByUserId);

            saveReceipt(pharmacyId, adjustment.clientOperationId(), SyncPushResultStatusEnum.APPLIED);
            return new SyncPushResultDto(adjustment.clientOperationId(), SyncPushResultStatusEnum.APPLIED, "Applied");

        } catch (ApiException conflict) {
            saveReceipt(pharmacyId, adjustment.clientOperationId(), SyncPushResultStatusEnum.CONFLICT);
            return new SyncPushResultDto(adjustment.clientOperationId(), SyncPushResultStatusEnum.CONFLICT, conflict.getMessage());
        } catch (Exception unexpected) {
            log.error("Failed to apply pushed stock adjustment {}", adjustment.clientOperationId(), unexpected);
            return new SyncPushResultDto(adjustment.clientOperationId(), SyncPushResultStatusEnum.FAILED, "Could not apply this change");
        }
    }

    private void saveReceipt(UUID pharmacyId, UUID clientOperationId, SyncPushResultStatusEnum status) {
        SyncPushReceiptEntity receipt = new SyncPushReceiptEntity();
        receipt.setPharmacyId(pharmacyId);
        receipt.setClientOperationId(clientOperationId);
        receipt.setStatus(status);
        receipt.setAppliedAt(Instant.now());
        syncPushReceiptRepository.save(receipt);
    }
}
