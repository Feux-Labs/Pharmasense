package com.pharmasense.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.agent.tool.AgentTool;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.agent.tool.JsonSchema;
import com.pharmasense.identity.enums.PermissionEnum;
import com.pharmasense.inventory.dto.InventoryItemCreateRequest;
import com.pharmasense.inventory.dto.InventoryItemResponse;
import com.pharmasense.inventory.entity.InventoryItemEntity;
import com.pharmasense.inventory.service.InventoryBatchService;
import com.pharmasense.inventory.service.InventoryItemService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** "Add Paracetamol 500mg at Rp 1200, I've got 100 in batch B123 expiring in December" */
@Component
public class AddInventoryItemTool implements AgentTool {

    private final InventoryItemService inventoryItemService;
    private final InventoryBatchService inventoryBatchService;

    public AddInventoryItemTool(InventoryItemService inventoryItemService, InventoryBatchService inventoryBatchService) {
        this.inventoryItemService = inventoryItemService;
        this.inventoryBatchService = inventoryBatchService;
    }

    @Override
    public String name() {
        return "add_inventory_item";
    }

    @Override
    public String description() {
        return "Create a new product in inventory. Optionally receive an initial batch of stock for it in the same step.";
    }

    @Override
    public PermissionEnum requiredPermission() {
        return PermissionEnum.INVENTORY_WRITE;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return JsonSchema.object()
                .string("name", "Product name, e.g. 'Paracetamol 500mg'")
                .string("category", "Optional product category, e.g. 'Pain relief'")
                .number("unitSellingPrice", "Selling price per unit")
                .bool("requiresPrescription", "Whether this product requires a prescription to sell")
                .integer("initialQuantity", "Optional: quantity to receive immediately as the first batch")
                .string("batchNumber", "Batch/lot number for the initial quantity (required if initialQuantity is given)")
                .string("expiryDate", "Expiry date of the initial batch, ISO format YYYY-MM-DD (optional)")
                .required("name", "unitSellingPrice")
                .build();
    }

    @Override
    public Object execute(AgentToolContext context, JsonNode input) {
        InventoryItemCreateRequest request = new InventoryItemCreateRequest(
                input.path("name").asText(),
                null,
                input.path("category").asText(null),
                null,
                null,
                "unit",
                new BigDecimal(input.path("unitSellingPrice").asText()),
                input.path("requiresPrescription").asBoolean(false),
                null);

        InventoryItemResponse createdItem = inventoryItemService.create(context.pharmacyId(), request);

        int initialQuantity = input.path("initialQuantity").asInt(0);
        if (initialQuantity > 0) {
            InventoryItemEntity itemEntity = inventoryItemService.getEntity(context.pharmacyId(), createdItem.id());
            String batchNumber = input.path("batchNumber").asText("AUTO-" + System.currentTimeMillis());
            LocalDate expiryDate = input.hasNonNull("expiryDate") ? LocalDate.parse(input.path("expiryDate").asText()) : null;

            inventoryBatchService.receiveBatch(itemEntity, batchNumber, initialQuantity, null, expiryDate, LocalDate.now(), context.userId());
            return inventoryItemService.getResponse(context.pharmacyId(), createdItem.id());
        }

        return createdItem;
    }
}
