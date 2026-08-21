package com.pharmasense.admin.controller;

import com.pharmasense.admin.dto.ImpersonationRequest;
import com.pharmasense.admin.dto.ImpersonationResponse;
import com.pharmasense.admin.service.ImpersonationService;
import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Click on their account and do anything they can do." Issues a short-lived
 * token scoped to the target user's own role and pharmacy - the frontend
 * swaps its active token for this one to render the app exactly as that user
 * would see it. Restricted to SUPER_ADMIN by the path rule in SecurityConfig.
 */
@Tag(name = "Admin / Impersonation")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminImpersonationController {

    private final ImpersonationService impersonationService;

    public AdminImpersonationController(ImpersonationService impersonationService) {
        this.impersonationService = impersonationService;
    }

    @PostMapping("/{userId}/impersonate")
    public ApiResponse<ImpersonationResponse> impersonate(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @PathVariable UUID userId,
            @RequestBody(required = false) ImpersonationRequest request) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(impersonationService.impersonate(principal.userId(), userId, reason));
    }
}
