package com.pharmasense.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.agent.tool.AgentTool;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.agent.tool.JsonSchema;
import com.pharmasense.identity.enums.PermissionEnum;
import com.pharmasense.inventory.service.InventoryItemService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** "What's expiring soon?" / "anything expired I should throw out?" */
@Component
public class CheckExpiringItemsTool implements AgentTool {

    private static final int DEFAULT_WITHIN_DAYS = 90;

    private final InventoryItemService inventoryItemService;

    public CheckExpiringItemsTool(InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    @Override
    public String name() {
        return "check_expiring_items";
    }

    @Override
    public String description() {
        return "List product batches that are expired or expiring soon.";
    }

    @Override
    public PermissionEnum requiredPermission() {
        return PermissionEnum.INVENTORY_READ;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return JsonSchema.object()
                .integer("withinDays", "How many days ahead to look for upcoming expiries (default 90)")
                .build();
    }

    @Override
    public Object execute(AgentToolContext context, JsonNode input) {
        int withinDays = input.path("withinDays").isMissingNode() ? DEFAULT_WITHIN_DAYS : input.path("withinDays").asInt(DEFAULT_WITHIN_DAYS);
        return inventoryItemService.getExpiryTracker(context.pharmacyId(), withinDays);
    }
}
