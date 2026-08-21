package com.pharmasense.catalog.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.catalog.dto.QrCodeAssignResponse;
import com.pharmasense.catalog.service.CatalogScanService;
import com.pharmasense.catalog.service.QrCodeImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The "walk the store scanning shelf labels" feature. A product QR encodes
 * {@code {frontendBaseUrl}/scan/{code}}; the staff PWA opens that route,
 * which calls {@code GET /scan/{code}} here with the logged-in user's bearer
 * token to resolve it into live stock data. Scanning is intentionally
 * staff-authenticated (not a public endpoint) since the response includes
 * cost/quantity data a pharmacy would not want a customer or competitor to
 * see just by photographing a shelf label.
 */
@Tag(name = "Catalog / QR")
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogScanService catalogScanService;
    private final QrCodeImageService qrCodeImageService;
    private final String frontendBaseUrl;

    public CatalogController(
            CatalogScanService catalogScanService,
            QrCodeImageService qrCodeImageService,
            @Value("${pharmasense.frontend.base-url}") String frontendBaseUrl) {
        this.catalogScanService = catalogScanService;
        this.qrCodeImageService = qrCodeImageService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping("/items/{itemId}/qrcode")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_WRITE')")
    public ApiResponse<QrCodeAssignResponse> assignItemQrCode(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable UUID itemId) {
        return ApiResponse.ok(catalogScanService.assignItemQrCode(principal.pharmacyId(), itemId));
    }

    @PostMapping("/batches/{batchId}/qrcode")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_WRITE')")
    public ApiResponse<QrCodeAssignResponse> assignBatchQrCode(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable UUID batchId) {
        return ApiResponse.ok(catalogScanService.assignBatchQrCode(principal.pharmacyId(), batchId));
    }

    @GetMapping("/scan/{code}")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'CATALOG_SCAN')")
    public ApiResponse<Object> scan(@AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable String code) {
        return ApiResponse.ok(catalogScanService.resolveScan(principal.pharmacyId(), code));
    }

    @GetMapping(value = "/qrcode/{code}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_READ')")
    public byte[] qrCodeImage(@AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable String code) {
        catalogScanService.resolveScan(principal.pharmacyId(), code); // throws 404 if the code doesn't belong to this pharmacy
        return qrCodeImageService.generatePng(frontendBaseUrl + "/scan/" + code);
    }
}
