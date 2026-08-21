package com.pharmasense.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SyncPushRequest(
        @NotEmpty @Valid List<PendingStockAdjustmentDto> stockAdjustments) {
}
