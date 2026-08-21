package com.pharmasense.prescription.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.common.response.PageResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.prescription.dto.PrescriptionCreateRequest;
import com.pharmasense.prescription.dto.PrescriptionResponse;
import com.pharmasense.prescription.dto.PrescriptionStatusUpdateRequest;
import com.pharmasense.prescription.enums.PrescriptionStatusEnum;
import com.pharmasense.prescription.service.PrescriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Prescriptions")
@RestController
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PRESCRIPTION_READ')")
    public ApiResponse<PageResponse<PrescriptionResponse>> list(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @RequestParam(required = false) PrescriptionStatusEnum status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(prescriptionService.list(principal.pharmacyId(), status, pageable)));
    }

    @GetMapping("/{prescriptionId}")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PRESCRIPTION_READ')")
    public ApiResponse<PrescriptionResponse> getById(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable UUID prescriptionId) {
        return ApiResponse.ok(prescriptionService.getResponse(principal.pharmacyId(), prescriptionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PRESCRIPTION_WRITE')")
    public ApiResponse<PrescriptionResponse> create(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @Valid @RequestBody PrescriptionCreateRequest request) {
        return ApiResponse.ok(prescriptionService.create(principal.pharmacyId(), request), "Prescription created");
    }

    @PatchMapping("/{prescriptionId}/status")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PRESCRIPTION_WRITE')")
    public ApiResponse<PrescriptionResponse> updateStatus(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @PathVariable UUID prescriptionId,
            @Valid @RequestBody PrescriptionStatusUpdateRequest request) {
        PrescriptionResponse updated = prescriptionService.updateStatus(
                principal.pharmacyId(), prescriptionId, request.status(), principal.userId());
        return ApiResponse.ok(updated, "Prescription status updated to " + request.status());
    }
}
