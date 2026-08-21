package com.pharmasense.sync.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.sync.dto.SyncPullResponse;
import com.pharmasense.sync.dto.SyncPushRequest;
import com.pharmasense.sync.dto.SyncPushResultDto;
import com.pharmasense.sync.dto.TenantSnapshotResponse;
import com.pharmasense.sync.service.SyncPullService;
import com.pharmasense.sync.service.SyncPushService;
import com.pharmasense.sync.service.TenantSnapshotService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Everything the offline-first client needs: {@code /snapshot} for the
 * initial/periodic full download, {@code /pull} for cheap incremental
 * catch-up in between, {@code /push} to replay queued offline writes once
 * back online.
 */
@Tag(name = "Sync")
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final TenantSnapshotService tenantSnapshotService;
    private final SyncPullService syncPullService;
    private final SyncPushService syncPushService;

    public SyncController(TenantSnapshotService tenantSnapshotService, SyncPullService syncPullService, SyncPushService syncPushService) {
        this.tenantSnapshotService = tenantSnapshotService;
        this.syncPullService = syncPullService;
        this.syncPushService = syncPushService;
    }

    @GetMapping("/snapshot")
    public ApiResponse<TenantSnapshotResponse> snapshot(@AuthenticationPrincipal PharmasenseUserPrincipal principal) {
        return ApiResponse.ok(tenantSnapshotService.buildSnapshot(principal.pharmacyId()));
    }

    @GetMapping("/pull")
    public ApiResponse<SyncPullResponse> pull(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @RequestParam(defaultValue = "0") long since) {
        return ApiResponse.ok(syncPullService.pull(principal.pharmacyId(), since));
    }

    @PostMapping("/push")
    public ApiResponse<List<SyncPushResultDto>> push(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @Valid @RequestBody SyncPushRequest request) {
        List<SyncPushResultDto> results = syncPushService.applyStockAdjustments(
                principal.pharmacyId(), principal.userId(), request.stockAdjustments());
        return ApiResponse.ok(results);
    }
}
