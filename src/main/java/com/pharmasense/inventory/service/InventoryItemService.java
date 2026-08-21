package com.pharmasense.inventory.service;

import com.pharmasense.common.exception.ResourceNotFoundException;
import com.pharmasense.inventory.dto.ExpiryTrackerEntryResponse;
import com.pharmasense.inventory.dto.InventoryItemCreateRequest;
import com.pharmasense.inventory.dto.InventoryItemResponse;
import com.pharmasense.inventory.dto.InventoryItemUpdateRequest;
import com.pharmasense.inventory.entity.InventoryBatchEntity;
import com.pharmasense.inventory.entity.InventoryItemEntity;
import com.pharmasense.inventory.repository.InventoryItemRepository;
import com.pharmasense.sync.enums.SyncEntityTypeEnum;
import com.pharmasense.sync.enums.SyncOperationEnum;
import com.pharmasense.sync.service.SyncChangeRecorder;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.service.PharmacyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryItemService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryBatchService inventoryBatchService;
    private final InventoryStatusCalculator statusCalculator;
    private final PharmacyService pharmacyService;
    private final SyncChangeRecorder syncChangeRecorder;

    public InventoryItemService(
            InventoryItemRepository inventoryItemRepository,
            InventoryBatchService inventoryBatchService,
            InventoryStatusCalculator statusCalculator,
            PharmacyService pharmacyService,
            SyncChangeRecorder syncChangeRecorder) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryBatchService = inventoryBatchService;
        this.statusCalculator = statusCalculator;
        this.pharmacyService = pharmacyService;
        this.syncChangeRecorder = syncChangeRecorder;
    }

    @Transactional
    public InventoryItemResponse create(UUID pharmacyId, InventoryItemCreateRequest request) {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setPharmacyId(pharmacyId);
        item.setName(request.name());
        item.setGenericName(request.genericName());
        item.setCategory(request.category());
        item.setSku(request.sku());
        item.setManufacturer(request.manufacturer());
        item.setUnit(StringUtils.hasText(request.unit()) ? request.unit() : "unit");
        item.setUnitSellingPrice(request.unitSellingPrice());
        item.setRequiresPrescription(request.requiresPrescription());
        item.setLowStockThreshold(request.lowStockThreshold());
        InventoryItemEntity saved = inventoryItemRepository.save(item);
        InventoryItemResponse response = toResponse(saved, pharmacyService.getById(pharmacyId));
        syncChangeRecorder.record(pharmacyId, SyncEntityTypeEnum.INVENTORY_ITEM, saved.getId(), SyncOperationEnum.CREATE, response);
        return response;
    }

    @Transactional
    public InventoryItemResponse update(UUID pharmacyId, UUID itemId, InventoryItemUpdateRequest request) {
        InventoryItemEntity item = getEntity(pharmacyId, itemId);
        item.setName(request.name());
        item.setGenericName(request.genericName());
        item.setCategory(request.category());
        item.setSku(request.sku());
        item.setManufacturer(request.manufacturer());
        item.setUnit(StringUtils.hasText(request.unit()) ? request.unit() : item.getUnit());
        item.setUnitSellingPrice(request.unitSellingPrice());
        item.setRequiresPrescription(request.requiresPrescription());
        item.setLowStockThreshold(request.lowStockThreshold());
        item.setActive(request.active());
        InventoryItemEntity saved = inventoryItemRepository.save(item);
        InventoryItemResponse response = toResponse(saved, pharmacyService.getById(pharmacyId));
        syncChangeRecorder.record(pharmacyId, SyncEntityTypeEnum.INVENTORY_ITEM, saved.getId(), SyncOperationEnum.UPDATE, response);
        return response;
    }

    @Transactional
    public void delete(UUID pharmacyId, UUID itemId) {
        InventoryItemEntity item = getEntity(pharmacyId, itemId);
        item.setActive(false);
        InventoryItemEntity saved = inventoryItemRepository.save(item);
        syncChangeRecorder.record(pharmacyId, SyncEntityTypeEnum.INVENTORY_ITEM, itemId, SyncOperationEnum.DELETE,
                toResponse(saved, pharmacyService.getById(pharmacyId)));
    }

    public InventoryItemResponse getResponse(UUID pharmacyId, UUID itemId) {
        InventoryItemEntity item = getEntity(pharmacyId, itemId);
        return toResponse(item, pharmacyService.getById(pharmacyId));
    }

    public InventoryItemEntity getEntity(UUID pharmacyId, UUID itemId) {
        return inventoryItemRepository.findByIdAndPharmacyId(itemId, pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", itemId));
    }

    public Page<InventoryItemResponse> list(UUID pharmacyId, String searchTerm, Pageable pageable) {
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        Page<InventoryItemEntity> page = StringUtils.hasText(searchTerm)
                ? inventoryItemRepository.findByPharmacyIdAndActiveTrueAndNameContainingIgnoreCase(pharmacyId, searchTerm, pageable)
                : inventoryItemRepository.findByPharmacyIdAndActiveTrue(pharmacyId, pageable);
        return page.map(item -> toResponse(item, pharmacy));
    }

    public InventoryItemResponse toResponse(InventoryItemEntity item, PharmacyEntity pharmacy) {
        List<InventoryBatchEntity> batches = inventoryBatchService.listBatchesForItem(item.getId());

        int totalQuantityOnHand = batches.stream().mapToInt(InventoryBatchEntity::getQuantityOnHand).sum();
        LocalDate nearestExpiryDate = batches.stream()
                .filter(batch -> batch.getQuantityOnHand() > 0 && batch.getExpiryDate() != null)
                .map(InventoryBatchEntity::getExpiryDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        int effectiveThreshold = item.getLowStockThreshold() != null
                ? item.getLowStockThreshold()
                : pharmacy.getLowStockThresholdDefault();

        return new InventoryItemResponse(
                item.getId(),
                item.getName(),
                item.getGenericName(),
                item.getCategory(),
                item.getSku(),
                item.getManufacturer(),
                item.getUnit(),
                item.getUnitSellingPrice(),
                item.isRequiresPrescription(),
                item.getLowStockThreshold(),
                item.isActive(),
                item.getQrCode(),
                totalQuantityOnHand,
                statusCalculator.computeStockLevelStatus(totalQuantityOnHand, effectiveThreshold),
                nearestExpiryDate,
                statusCalculator.computeExpiryStatus(nearestExpiryDate, pharmacy.getExpiryWarningDaysDefault()),
                item.getCreatedAt());
    }

    /** Feeds the expiry tracker screen: every batch with stock remaining that expires within {@code withinDays}, or already has. */
    public List<ExpiryTrackerEntryResponse> getExpiryTracker(UUID pharmacyId, int withinDays) {
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        LocalDate today = LocalDate.now();

        List<InventoryBatchEntity> expired = inventoryBatchService.listExpiredBefore(pharmacyId, today);
        List<InventoryBatchEntity> expiringSoon = inventoryBatchService.listExpiringBetween(pharmacyId, today, today.plusDays(withinDays));

        List<InventoryBatchEntity> allBatches = java.util.stream.Stream.concat(expired.stream(), expiringSoon.stream())
                .filter(batch -> batch.getQuantityOnHand() > 0)
                .distinct()
                .toList();

        Map<UUID, String> itemNamesById = inventoryItemRepository
                .findByPharmacyIdAndIdIn(pharmacyId, allBatches.stream().map(InventoryBatchEntity::getInventoryItemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(InventoryItemEntity::getId, InventoryItemEntity::getName));

        return allBatches.stream()
                .map(batch -> new ExpiryTrackerEntryResponse(
                        batch.getId(),
                        batch.getInventoryItemId(),
                        itemNamesById.getOrDefault(batch.getInventoryItemId(), "Unknown item"),
                        batch.getBatchNumber(),
                        batch.getQuantityOnHand(),
                        batch.getExpiryDate(),
                        ChronoUnit.DAYS.between(today, batch.getExpiryDate()),
                        statusCalculator.computeExpiryStatus(batch.getExpiryDate(), pharmacy.getExpiryWarningDaysDefault())))
                .sorted((a, b) -> a.expiryDate().compareTo(b.expiryDate()))
                .toList();
    }

    @Transactional
    public InventoryItemEntity assignQrCode(UUID pharmacyId, UUID itemId, String qrCode) {
        InventoryItemEntity item = getEntity(pharmacyId, itemId);
        item.setQrCode(qrCode);
        return inventoryItemRepository.save(item);
    }

    /** Looks an item up by its scan code and enforces it belongs to the scanning pharmacy in one step. */
    public InventoryItemEntity findByQrCodeForPharmacy(UUID pharmacyId, String qrCode) {
        InventoryItemEntity item = inventoryItemRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Scan code", qrCode));
        if (!item.getPharmacyId().equals(pharmacyId)) {
            throw new ResourceNotFoundException("Scan code", qrCode);
        }
        return item;
    }
}
