package com.pharmasense.billing.dto;

public record PlanLimitsResponse(int maxInventoryItems, int maxStaffUsers, int maxAgentMessagesPerMonth) {
}
