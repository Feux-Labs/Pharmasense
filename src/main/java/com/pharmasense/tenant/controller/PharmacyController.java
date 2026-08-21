package com.pharmasense.tenant.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.tenant.dto.PharmacyResponse;
import com.pharmasense.tenant.dto.PharmacyUpdateRequest;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.mapper.PharmacyMapper;
import com.pharmasense.tenant.service.PharmacyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pharmacy")
@RestController
@RequestMapping("/api/v1/pharmacy")
public class PharmacyController {

    private final PharmacyService pharmacyService;
    private final PharmacyMapper pharmacyMapper;

    public PharmacyController(PharmacyService pharmacyService, PharmacyMapper pharmacyMapper) {
        this.pharmacyService = pharmacyService;
        this.pharmacyMapper = pharmacyMapper;
    }

    @GetMapping("/me")
    public ApiResponse<PharmacyResponse> getMyPharmacy(@AuthenticationPrincipal PharmasenseUserPrincipal principal) {
        PharmacyEntity pharmacy = pharmacyService.getById(principal.pharmacyId());
        return ApiResponse.ok(pharmacyMapper.toResponse(pharmacy));
    }

    @PatchMapping("/me")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PHARMACY_SETTINGS_MANAGE')")
    public ApiResponse<PharmacyResponse> updateMyPharmacy(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @Valid @RequestBody PharmacyUpdateRequest request) {
        PharmacyEntity pharmacy = pharmacyService.getById(principal.pharmacyId());
        pharmacyMapper.applyUpdate(request, pharmacy);
        PharmacyEntity saved = pharmacyService.save(pharmacy);
        return ApiResponse.ok(pharmacyMapper.toResponse(saved), "Pharmacy profile updated");
    }
}
