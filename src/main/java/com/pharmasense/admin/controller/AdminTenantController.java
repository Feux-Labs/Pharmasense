package com.pharmasense.admin.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.common.response.PageResponse;
import com.pharmasense.tenant.dto.PharmacyResponse;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.mapper.PharmacyMapper;
import com.pharmasense.tenant.service.PharmacyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Platform-side view of every pharmacy on Pharmasense. Restricted to SUPER_ADMIN by the path rule in SecurityConfig. */
@Tag(name = "Admin / Tenants")
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class AdminTenantController {

    private final PharmacyService pharmacyService;
    private final PharmacyMapper pharmacyMapper;

    public AdminTenantController(PharmacyService pharmacyService, PharmacyMapper pharmacyMapper) {
        this.pharmacyService = pharmacyService;
        this.pharmacyMapper = pharmacyMapper;
    }

    @GetMapping
    public ApiResponse<PageResponse<PharmacyResponse>> list(Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(pharmacyService.listAll(pageable).map(pharmacyMapper::toResponse)));
    }

    @GetMapping("/{pharmacyId}")
    public ApiResponse<PharmacyResponse> getById(@PathVariable UUID pharmacyId) {
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        return ApiResponse.ok(pharmacyMapper.toResponse(pharmacy));
    }
}
