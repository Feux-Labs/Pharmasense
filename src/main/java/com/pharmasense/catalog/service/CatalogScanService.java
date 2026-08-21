package com.pharmasense.catalog.service;

import com.pharmasense.catalog.dto.CatalogBatchScanResponse;
import com.pharmasense.catalog.dto.CatalogItemScanResponse;
import com.pharmasense.catalog.dto.QrCodeAssignResponse;
import com.pharmasense.common.exception.ResourceNotFoundException;
import com.pharmasense.inventory.entity.InventoryBatchEntity;
import com.pharmasense.inventory.entity.InventoryItemEntity;
import com.pharmasense.inventory.service.InventoryBatchService;
import com.pharmasense.inventory.service.InventoryItemService;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.service.PharmacyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Assigns scan codes to items/batches and resolves a scanned code back into
 * live data. Tenant isolation matters more here than almost anywhere else in
 * the API: a code is looked up globally (by its unique value, with no
 * pharmacy_id in the query), so every resolve path re-checks the result
 * belongs to the scanning user's pharmacy before returning anything - a
 * pharmacy must never be able to see another pharmacy's inventory just by
 * guessing or brute-forcing a scan code.
 */
@Service
public class CatalogScanService {

    private final InventoryItemService inventoryItemService;
    private final InventoryBatchService inventoryBatchService;
    private final PharmacyService pharmacyService;
    private final CatalogCodeGenerator codeGenerator;
    private final String frontendBaseUrl;

    public CatalogScanService(
            InventoryItemService inventoryItemService,
            InventoryBatchService inventoryBatchService,
            PharmacyService pharmacyService,
            CatalogCodeGenerator codeGenerator,
            @Value("${pharmasense.frontend.base-url}") String frontendBaseUrl) {
        this.inventoryItemService = inventoryItemService;
        this.inventoryBatchService = inventoryBatchService;
        this.pharmacyService = pharmacyService;
        this.codeGenerator = codeGenerator;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public QrCodeAssignResponse assignItemQrCode(UUID pharmacyId, UUID itemId) {
        InventoryItemEntity item = inventoryItemService.getEntity(pharmacyId, itemId);
        String code = item.getQrCode() != null ? item.getQrCode() : codeGenerator.generateItemCode();
        if (item.getQrCode() == null) {
            inventoryItemService.assignQrCode(pharmacyId, itemId, code);
        }
        return toAssignResponse(code);
    }

    @Transactional
    public QrCodeAssignResponse assignBatchQrCode(UUID pharmacyId, UUID batchId) {
        InventoryBatchEntity batch = inventoryBatchService.getByIdForPharmacy(pharmacyId, batchId);
        String code = batch.getQrCode() != null ? batch.getQrCode() : codeGenerator.generateBatchCode();
        if (batch.getQrCode() == null) {
            inventoryBatchService.assignQrCode(pharmacyId, batchId, code);
        }
        return toAssignResponse(code);
    }

    @Cacheable(cacheNames = "catalogScans", key = "#pharmacyId + ':' + #code")
    public Object resolveScan(UUID pharmacyId, String code) {
        if (code.startsWith(CatalogCodeGenerator.BATCH_PREFIX)) {
            return resolveBatchScan(pharmacyId, code);
        }
        return resolveItemScan(pharmacyId, code);
    }

    private CatalogItemScanResponse resolveItemScan(UUID pharmacyId, String code) {
        InventoryItemEntity item = inventoryItemService.findByQrCodeForPharmacy(pharmacyId, code);
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        List<InventoryBatchEntity> batches = inventoryBatchService.listBatchesForItem(item.getId());

        List<com.pharmasense.inventory.dto.InventoryBatchResponse> batchResponses = batches.stream()
                .map(batch -> inventoryBatchService.toResponse(batch, pharmacy.getExpiryWarningDaysDefault()))
                .toList();

        return new CatalogItemScanResponse(inventoryItemService.toResponse(item, pharmacy), batchResponses);
    }

    private CatalogBatchScanResponse resolveBatchScan(UUID pharmacyId, String code) {
        InventoryBatchEntity batch = inventoryBatchService.getByQrCode(code);
        if (!batch.getPharmacyId().equals(pharmacyId)) {
            throw new ResourceNotFoundException("Scan code", code);
        }
        InventoryItemEntity item = inventoryItemService.getEntity(pharmacyId, batch.getInventoryItemId());
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);

        return new CatalogBatchScanResponse(
                inventoryBatchService.toResponse(batch, pharmacy.getExpiryWarningDaysDefault()),
                item.getId(), item.getName(), item.getCategory(), item.getUnitSellingPrice());
    }

    private QrCodeAssignResponse toAssignResponse(String code) {
        return new QrCodeAssignResponse(
                code,
                frontendBaseUrl + "/scan/" + code,
                "/api/v1/catalog/qrcode/" + code + ".png");
    }
}
