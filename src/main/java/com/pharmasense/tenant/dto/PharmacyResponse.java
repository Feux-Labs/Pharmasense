package com.pharmasense.tenant.dto;

import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;

import java.time.Instant;
import java.util.UUID;

public record PharmacyResponse(
        UUID id,
        String name,
        String contactEmail,
        String contactPhone,
        String addressLine,
        String city,
        String country,
        String taxRegistrationNumber,
        String currencyCode,
        String timezone,
        PharmacyPlanEnum plan,
        PharmacySubscriptionStatusEnum subscriptionStatus,
        Instant trialEndsAt,
        int lowStockThresholdDefault,
        int expiryWarningDaysDefault,
        Instant createdAt) {
}
