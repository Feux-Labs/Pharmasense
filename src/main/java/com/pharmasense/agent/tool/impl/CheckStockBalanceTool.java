package com.pharmasense.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.agent.tool.AgentTool;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.agent.tool.JsonSchema;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.enums.PermissionEnum;
import com.pharmasense.inventory.dto.InventoryItemResponse;
import com.pharmasense.inventory.service.InventoryItemService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Map;

/** "How much paracetamol do we have left?" / "what's my stock balance on X?" */
@Component
public class CheckStockBalanceTool implements AgentTool {

    private final InventoryItemService inventoryItemService;

    public CheckStockBalanceTool(InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    @Override
    public String name() {
        return "check_stock_balance";
    }

    @Override
    public String description() {
        return "Look up how much stock is currently on hand for a product by name, along with its price and stock/expiry status.";
    }

    @Override
    public PermissionEnum requiredPermission() {
        return PermissionEnum.INVENTORY_READ;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return JsonSchema.object()
                .string("itemName", "The product name or a close match, e.g. 'paracetamol' or 'vitamin c'")
                .required("itemName")
                .build();
    }

    @Override
    public Object execute(AgentToolContext context, JsonNode input) {
        String itemName = input.path("itemName").asText();
        Page<InventoryItemResponse> matches = inventoryItemService.list(context.pharmacyId(), itemName, PageRequest.of(0, 5));

        if (matches.isEmpty()) {
            throw new ApiException(ErrorCode.AGENT_TOOL_EXECUTION_FAILED, "No product found matching \"" + itemName + "\"");
        }
        if (matches.getContent().size() > 1) {
            return Map.of(
                    "ambiguous", true,
                    "message", "Multiple products match \"" + itemName + "\" - ask the user which one they mean",
                    "candidates", matches.getContent().stream().map(InventoryItemResponse::name).toList());
        }
        return matches.getContent().get(0);
    }
}
