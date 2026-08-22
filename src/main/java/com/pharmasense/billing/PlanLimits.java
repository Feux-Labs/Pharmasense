package com.pharmasense.billing;

import com.pharmasense.tenant.enums.PharmacyPlanEnum;

import java.util.Map;

/**
 * What each self-serve plan actually caps. PRO and ENTERPRISE are
 * unlimited (Integer.MAX_VALUE is a deliberate stand-in for "no limit",
 * not a real ceiling anyone should ever reach).
 */
public record PlanLimits(int maxInventoryItems, int maxStaffUsers, int maxAgentMessagesPerMonth) {

    private static final PlanLimits FREE = new PlanLimits(50, 1, 20);
    private static final PlanLimits BASIC = new PlanLimits(500, 3, 300);
    private static final PlanLimits UNLIMITED = new PlanLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private static final Map<PharmacyPlanEnum, PlanLimits> BY_PLAN = Map.of(
            PharmacyPlanEnum.FREE, FREE,
            PharmacyPlanEnum.BASIC, BASIC,
            PharmacyPlanEnum.PRO, UNLIMITED,
            PharmacyPlanEnum.ENTERPRISE, UNLIMITED);

    public static PlanLimits of(PharmacyPlanEnum plan) {
        return BY_PLAN.get(plan);
    }
}
