package com.pharmasense.billing.dto;

public record PlanUsageResponse(long inventoryItemCount, long staffCount, long agentMessagesThisMonth) {
}
