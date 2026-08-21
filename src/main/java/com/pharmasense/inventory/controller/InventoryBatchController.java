package com.pharmasense.inventory.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.inventory.dto.InventoryBatchCreateRequest;
import com.pharmasense.inventory.dto.InventoryBatchResponse;
import com.pharmasense.inventory.dto.StockAdjustmentRequest;
import com.pharmasense.inventory.entity.InventoryBatchEntity;
import com.pharmasense.inventory.entity.InventoryItemEntity;
import com.pharmasense.inventory.service.InventoryBatchService;
import com.pharmasense.inventory.service.InventoryItemService;
import com.pharmasense.tenant.service.PharmacyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Inventory Batches")
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryBatchController {

    private final InventoryBatchService inventoryBatchService;
    private final InventoryItemService inventoryItemService;
    private final PharmacyService pharmacyService;

    public InventoryBatchController(
            InventoryBatchService inventoryBatchService, InventoryItemService inventoryItemService, PharmacyService pharmacyService) {
        this.inventoryBatchService = inventoryBatchService;
        this.inventoryItemService = inventoryItemService;
        this.pharmacyService = pharmacyService;
    }

    @GetMapping("/items/{itemId}/batches")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_READ')")
    public ApiResponse<List<InventoryBatchResponse>> listBatches(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable UUID itemId) {
        inventoryItemService.getEntity(principal.pharmacyId(), itemId); // 404s if the item isn't this pharmacy's
        int expiryWarningDays = pharmacyService.getById(principal.pharmacyId()).getExpiryWarningDaysDefault();
        List<InventoryBatchResponse> batches = inventoryBatchService.listBatchesForItem(itemId).stream()
                .map(batch -> inventoryBatchService.toResponse(batch, expiryWarningDays))
                .toList();
        return ApiResponse.ok(batches);
    }

    @PostMapping("/items/{itemId}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_WRITE')")
    public ApiResponse<InventoryBatchResponse> receiveBatch(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryBatchCreateRequest request) {
        InventoryItemEntity item = inventoryItemService.getEntity(principal.pharmacyId(), itemId);
        InventoryBatchEntity batch = inventoryBatchService.receiveBatch(
                item, request.batchNumber(), request.quantityOnHand(), request.unitCostPrice(),
                request.expiryDate(), request.receivedAt(), principal.userId());
        int expiryWarningDays = pharmacyService.getById(principal.pharmacyId()).getExpiryWarningDaysDefault();
        return ApiResponse.ok(inventoryBatchService.toResponse(batch, expiryWarningDays), "Stock received");
    }

    @PostMapping("/batches/{batchId}/adjust")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_WRITE')")
    public ApiResponse<InventoryBatchResponse> adjustBatch(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @PathVariable UUID batchId,
            @Valid @RequestBody StockAdjustmentRequest request) {
        InventoryBatchEntity batch = inventoryBatchService.adjustQuantity(
                principal.pharmacyId(), batchId, request.movementType(), request.quantityDelta(), request.reason(), principal.userId());
        int expiryWarningDays = pharmacyService.getById(principal.pharmacyId()).getExpiryWarningDaysDefault();
        return ApiResponse.ok(inventoryBatchService.toResponse(batch, expiryWarningDays), "Stock adjusted");
    }
}
