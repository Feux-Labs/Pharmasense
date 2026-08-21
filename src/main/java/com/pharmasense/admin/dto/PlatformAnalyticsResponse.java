package com.pharmasense.admin.dto;

import java.time.Instant;

public record PlatformAnalyticsResponse(
        long totalPharmacies,
        long trialingPharmacies,
        long activePharmacies,
        long totalUsers,
        long totalInventoryItems,
        long totalPrescriptions,
        Instant generatedAt) {
}
