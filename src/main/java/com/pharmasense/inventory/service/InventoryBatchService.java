package com.pharmasense.inventory.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.common.exception.ResourceNotFoundException;
import com.pharmasense.inventory.dto.InventoryBatchResponse;
import com.pharmasense.inventory.entity.InventoryBatchEntity;
import com.pharmasense.inventory.entity.InventoryItemEntity;
import com.pharmasense.inventory.entity.StockMovementEntity;
import com.pharmasense.inventory.enums.StockMovementTypeEnum;
import com.pharmasense.inventory.repository.InventoryBatchRepository;
import com.pharmasense.inventory.repository.StockMovementRepository;
import com.pharmasense.sync.enums.SyncEntityTypeEnum;
import com.pharmasense.sync.enums.SyncOperationEnum;
import com.pharmasense.sync.service.SyncChangeRecorder;
import com.pharmasense.tenant.service.PharmacyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Owns batch-level stock: receiving new deliveries, manual adjustments
 * (sales, disposal, correction, returns), and FEFO batch selection. Every
 * quantity change writes a {@link StockMovementEntity} row in the same
 * transaction, so the movement log is always a complete, trustworthy audit
 * trail - nothing changes {@code quantityOnHand} without also recording why.
 */
@Service
public class InventoryBatchService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryStatusCalculator statusCalculator;
    private final PharmacyService pharmacyService;
    private final SyncChangeRecorder syncChangeRecorder;

    public InventoryBatchService(
            InventoryBatchRepository inventoryBatchRepository,
            StockMovementRepository stockMovementRepository,
            InventoryStatusCalculator statusCalculator,
            PharmacyService pharmacyService,
            SyncChangeRecorder syncChangeRecorder) {
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.statusCalculator = statusCalculator;
        this.pharmacyService = pharmacyService;
        this.syncChangeRecorder = syncChangeRecorder;
    }

    public InventoryBatchResponse toResponse(InventoryBatchEntity batch, int expiryWarningDays) {
        return new InventoryBatchResponse(
                batch.getId(),
                batch.getInventoryItemId(),
                batch.getBatchNumber(),
                batch.getQuantityOnHand(),
                batch.getUnitCostPrice(),
                batch.getExpiryDate(),
                batch.getReceivedAt(),
                batch.getQrCode(),
                statusCalculator.computeExpiryStatus(batch.getExpiryDate(), expiryWarningDays));
    }

    @Transactional
    public InventoryBatchEntity receiveBatch(
            InventoryItemEntity item, String batchNumber, int quantity,
            java.math.BigDecimal unitCostPrice, LocalDate expiryDate, LocalDate receivedAt, UUID performedByUserId) {

        InventoryBatchEntity batch = new InventoryBatchEntity();
        batch.setPharmacyId(item.getPharmacyId());
        batch.setInventoryItemId(item.getId());
        batch.setBatchNumber(batchNumber);
        batch.setQuantityOnHand(quantity);
        batch.setUnitCostPrice(unitCostPrice);
        batch.setExpiryDate(expiryDate);
        batch.setReceivedAt(receivedAt != null ? receivedAt : LocalDate.now());
        InventoryBatchEntity saved = inventoryBatchRepository.save(batch);

        recordMovement(saved, StockMovementTypeEnum.RECEIVED, quantity, "Stock received", performedByUserId);
        recordSyncChange(saved, SyncOperationEnum.CREATE);
        return saved;
    }

    @Transactional
    public InventoryBatchEntity adjustQuantity(
            UUID pharmacyId, UUID batchId, StockMovementTypeEnum movementType, int quantityDelta, String reason, UUID performedByUserId) {

        InventoryBatchEntity batch = getByIdForPharmacy(pharmacyId, batchId);
        int newQuantity = batch.getQuantityOnHand() + quantityDelta;
        if (newQuantity < 0) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "Adjustment would take batch " + batch.getBatchNumber() + " below zero stock");
        }
        batch.setQuantityOnHand(newQuantity);
        InventoryBatchEntity saved = inventoryBatchRepository.save(batch);

        recordMovement(saved, movementType, quantityDelta, reason, performedByUserId);
        recordSyncChange(saved, SyncOperationEnum.UPDATE);
        return saved;
    }

    public InventoryBatchEntity getByIdForPharmacy(UUID pharmacyId, UUID batchId) {
        return inventoryBatchRepository.findByIdAndPharmacyId(batchId, pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory batch", batchId));
    }

    public List<InventoryBatchEntity> listBatchesForItem(UUID inventoryItemId) {
        return inventoryBatchRepository.findByInventoryItemIdOrderByExpiryDateAsc(inventoryItemId);
    }

    /** Every batch for the pharmacy, regardless of item - used to build the offline snapshot bundle. */
    public List<InventoryBatchEntity> listAllForPharmacy(UUID pharmacyId) {
        return inventoryBatchRepository.findByPharmacyId(pharmacyId);
    }

    /** FEFO: the batch with stock remaining that expires soonest, or null if the item is fully out of stock. */
    public InventoryBatchEntity pickBatchForSale(UUID inventoryItemId) {
        List<InventoryBatchEntity> candidates = listAvailableBatchesFefo(inventoryItemId);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** Every batch with stock remaining, nearest-expiry first - used when a single batch isn't enough to cover a sale. */
    public List<InventoryBatchEntity> listAvailableBatchesFefo(UUID inventoryItemId) {
        return inventoryBatchRepository.findByInventoryItemIdAndQuantityOnHandGreaterThanOrderByExpiryDateAscNullsLast(inventoryItemId, 0);
    }

    public List<InventoryBatchEntity> listExpiringBetween(UUID pharmacyId, LocalDate from, LocalDate to) {
        return inventoryBatchRepository.findByPharmacyIdAndExpiryDateBetween(pharmacyId, from, to);
    }

    public List<InventoryBatchEntity> listExpiredBefore(UUID pharmacyId, LocalDate before) {
        return inventoryBatchRepository.findByPharmacyIdAndExpiryDateLessThan(pharmacyId, before);
    }

    public InventoryBatchEntity getByQrCode(String qrCode) {
        return inventoryBatchRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory batch", qrCode));
    }

    @Transactional
    public InventoryBatchEntity assignQrCode(UUID pharmacyId, UUID batchId, String qrCode) {
        InventoryBatchEntity batch = getByIdForPharmacy(pharmacyId, batchId);
        batch.setQrCode(qrCode);
        return inventoryBatchRepository.save(batch);
    }

    private void recordSyncChange(InventoryBatchEntity batch, SyncOperationEnum operation) {
        int expiryWarningDays = pharmacyService.getById(batch.getPharmacyId()).getExpiryWarningDaysDefault();
        syncChangeRecorder.record(batch.getPharmacyId(), SyncEntityTypeEnum.INVENTORY_BATCH, batch.getId(), operation, toResponse(batch, expiryWarningDays));
    }

    private void recordMovement(InventoryBatchEntity batch, StockMovementTypeEnum type, int quantityDelta, String reason, UUID performedByUserId) {
        StockMovementEntity movement = new StockMovementEntity();
        movement.setPharmacyId(batch.getPharmacyId());
        movement.setInventoryBatchId(batch.getId());
        movement.setInventoryItemId(batch.getInventoryItemId());
        movement.setMovementType(type);
        movement.setQuantityDelta(quantityDelta);
        movement.setReason(reason);
        movement.setPerformedByUserId(performedByUserId);
        stockMovementRepository.save(movement);
    }
}
