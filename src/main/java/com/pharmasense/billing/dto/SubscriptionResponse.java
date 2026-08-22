package com.pharmasense.billing.dto;

import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;

import java.time.Instant;

public record SubscriptionResponse(
        PharmacyPlanEnum plan,
        PharmacySubscriptionStatusEnum subscriptionStatus,
        Instant trialEndsAt,
        Instant currentPeriodEndsAt,
        boolean complimentary,
        PlanLimitsResponse limits,
        PlanUsageResponse usage) {
}
