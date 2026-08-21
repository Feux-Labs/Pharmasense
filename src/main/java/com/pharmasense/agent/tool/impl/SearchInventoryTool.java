package com.pharmasense.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.agent.tool.AgentTool;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.agent.tool.JsonSchema;
import com.pharmasense.identity.enums.PermissionEnum;
import com.pharmasense.inventory.service.InventoryItemService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/** "What do we have in the pain relief category?" / "search for vitamin" */
@Component
public class SearchInventoryTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 10;

    private final InventoryItemService inventoryItemService;

    public SearchInventoryTool(InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    @Override
    public String name() {
        return "search_inventory";
    }

    @Override
    public String description() {
        return "Search the pharmacy's inventory by product name keyword, returning matching items with stock status.";
    }

    @Override
    public PermissionEnum requiredPermission() {
        return PermissionEnum.INVENTORY_READ;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return JsonSchema.object()
                .string("query", "A keyword to search product names for")
                .integer("limit", "Maximum results to return (default 10)")
                .required("query")
                .build();
    }

    @Override
    public Object execute(AgentToolContext context, JsonNode input) {
        String query = input.path("query").asText();
        int limit = input.path("limit").isMissingNode() ? DEFAULT_LIMIT : input.path("limit").asInt(DEFAULT_LIMIT);
        return inventoryItemService.list(context.pharmacyId(), query, PageRequest.of(0, Math.min(limit, 25))).getContent();
    }
}
