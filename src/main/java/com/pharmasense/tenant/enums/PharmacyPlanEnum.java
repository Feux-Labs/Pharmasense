package com.pharmasense.tenant.enums;

/**
 * Self-serve pricing tiers. ENTERPRISE is reserved for manually-negotiated
 * deals - it's not purchasable through {@code POST /api/v1/billing/checkout}
 * and carries the same unlimited limits as PRO (see {@link PlanLimits}).
 */
public enum PharmacyPlanEnum {
    FREE,
    BASIC,
    PRO,
    ENTERPRISE
}
