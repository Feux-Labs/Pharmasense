package com.pharmasense.prescription.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.common.response.PageResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.prescription.dto.PatientCreateRequest;
import com.pharmasense.prescription.dto.PatientResponse;
import com.pharmasense.prescription.entity.PatientEntity;
import com.pharmasense.prescription.mapper.PatientMapper;
import com.pharmasense.prescription.service.PatientService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Patients")
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;
    private final PatientMapper patientMapper;

    public PatientController(PatientService patientService, PatientMapper patientMapper) {
        this.patientService = patientService;
        this.patientMapper = patientMapper;
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PRESCRIPTION_READ')")
    public ApiResponse<PageResponse<PatientResponse>> list(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(patientService.list(principal.pharmacyId(), search, pageable).map(patientMapper::toResponse)));
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PRESCRIPTION_READ')")
    public ApiResponse<PatientResponse> getById(@AuthenticationPrincipal PharmasenseUserPrincipal principal, @PathVariable UUID patientId) {
        PatientEntity patient = patientService.getEntity(principal.pharmacyId(), patientId);
        return ApiResponse.ok(patientMapper.toResponse(patient));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'PRESCRIPTION_WRITE')")
    public ApiResponse<PatientResponse> create(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @Valid @RequestBody PatientCreateRequest request) {
        PatientEntity created = patientService.create(principal.pharmacyId(), request);
        return ApiResponse.ok(patientMapper.toResponse(created), "Patient added");
    }
}
