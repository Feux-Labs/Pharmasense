package com.pharmasense.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.agent.tool.AgentTool;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.agent.tool.JsonSchema;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.enums.PermissionEnum;
import com.pharmasense.inventory.dto.InventoryItemResponse;
import com.pharmasense.inventory.entity.InventoryBatchEntity;
import com.pharmasense.inventory.enums.StockMovementTypeEnum;
import com.pharmasense.inventory.service.InventoryBatchService;
import com.pharmasense.inventory.service.InventoryItemService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Map;

/** "We sold 3 boxes of vitamin C" / "dispose 5 expired paracetamol" */
@Component
public class AdjustStockTool implements AgentTool {

    private final InventoryItemService inventoryItemService;
    private final InventoryBatchService inventoryBatchService;

    public AdjustStockTool(InventoryItemService inventoryItemService, InventoryBatchService inventoryBatchService) {
        this.inventoryItemService = inventoryItemService;
        this.inventoryBatchService = inventoryBatchService;
    }

    @Override
    public String name() {
        return "adjust_stock";
    }

    @Override
    public String description() {
        return "Record a stock change for a product - a sale, disposal, correction, or return. Applies to the batch "
                + "expiring soonest (FEFO). Use a negative quantityDelta for anything reducing stock.";
    }

    @Override
    public PermissionEnum requiredPermission() {
        return PermissionEnum.INVENTORY_WRITE;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return JsonSchema.object()
                .string("itemName", "The product name to adjust")
                .integer("quantityDelta", "Change in quantity - negative to reduce stock, positive to add")
                .string("movementType", "One of: SOLD, ADJUSTED, DISPOSED, RETURNED")
                .string("reason", "Short reason for the adjustment")
                .required("itemName", "quantityDelta", "movementType")
                .build();
    }

    @Override
    public Object execute(AgentToolContext context, JsonNode input) {
        String itemName = input.path("itemName").asText();
        Page<InventoryItemResponse> matches = inventoryItemService.list(context.pharmacyId(), itemName, PageRequest.of(0, 2));
        if (matches.isEmpty()) {
            throw new ApiException(ErrorCode.AGENT_TOOL_EXECUTION_FAILED, "No product found matching \"" + itemName + "\"");
        }
        if (matches.getContent().size() > 1) {
            throw new ApiException(ErrorCode.AGENT_TOOL_EXECUTION_FAILED,
                    "\"" + itemName + "\" matches more than one product - ask the user to be more specific");
        }
        InventoryItemResponse item = matches.getContent().get(0);

        InventoryBatchEntity batch = inventoryBatchService.pickBatchForSale(item.id());
        if (batch == null) {
            throw new ApiException(ErrorCode.AGENT_TOOL_EXECUTION_FAILED,
                    "\"" + item.name() + "\" has no batch with stock to adjust - it needs to be received as a batch first");
        }

        StockMovementTypeEnum movementType = StockMovementTypeEnum.valueOf(input.path("movementType").asText());
        int quantityDelta = input.path("quantityDelta").asInt();
        String reason = input.path("reason").asText(null);

        InventoryBatchEntity updated = inventoryBatchService.adjustQuantity(
                context.pharmacyId(), batch.getId(), movementType, quantityDelta, reason, context.userId());

        return Map.of(
                "itemName", item.name(),
                "batchNumber", updated.getBatchNumber(),
                "newQuantityOnHand", updated.getQuantityOnHand());
    }
}
