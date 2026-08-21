package com.pharmasense.inventory.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.common.response.PageResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.inventory.dto.ExpiryTrackerEntryResponse;
import com.pharmasense.inventory.dto.InventoryItemCreateRequest;
import com.pharmasense.inventory.dto.InventoryItemResponse;
import com.pharmasense.inventory.dto.InventoryItemUpdateRequest;
import com.pharmasense.inventory.service.InventoryItemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Inventory")
@RestController
@RequestMapping("/api/v1/inventory/items")
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    public InventoryItemController(InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_READ')")
    public ApiResponse<PageResponse<InventoryItemResponse>> list(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(inventoryItemService.list(principal.pharmacyId(), search, pageable)));
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_READ')")
    public ApiResponse<InventoryItemResponse> getById(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable UUID itemId) {
        return ApiResponse.ok(inventoryItemService.getResponse(principal.pharmacyId(), itemId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_WRITE')")
    public ApiResponse<InventoryItemResponse> create(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @Valid @RequestBody InventoryItemCreateRequest request) {
        return ApiResponse.ok(inventoryItemService.create(principal.pharmacyId(), request), "Item created");
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_WRITE')")
    public ApiResponse<InventoryItemResponse> update(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryItemUpdateRequest request) {
        return ApiResponse.ok(inventoryItemService.update(principal.pharmacyId(), itemId, request), "Item updated");
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_DELETE')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable UUID itemId) {
        inventoryItemService.delete(principal.pharmacyId(), itemId);
        return ApiResponse.ok(null, "Item deactivated");
    }

    @GetMapping("/expiry-tracker")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_READ')")
    public ApiResponse<List<ExpiryTrackerEntryResponse>> expiryTracker(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @RequestParam(defaultValue = "90") int withinDays) {
        return ApiResponse.ok(inventoryItemService.getExpiryTracker(principal.pharmacyId(), withinDays));
    }
}
