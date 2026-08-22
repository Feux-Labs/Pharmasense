package com.pharmasense.billing.dto;

import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(@NotNull PharmacyPlanEnum plan) {
}
