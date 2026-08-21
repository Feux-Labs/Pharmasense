package com.pharmasense.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.identity.enums.PermissionEnum;

import java.util.Map;

/**
 * One capability the assistant can invoke. Implementations should stay a
 * thin adapter over an existing feature service (e.g. {@code InventoryItemService})
 * - a tool is never the place to put business logic that doesn't already
 * exist for the regular REST API, since the same rules (validation,
 * tenant scoping, sync-change recording) must apply whether a human clicked
 * a button or the assistant did.
 */
public interface AgentTool {

    String name();

    String description();

    /** Null means every authenticated role may use this tool (e.g. a read-only balance check). */
    PermissionEnum requiredPermission();

    /** JSON Schema (draft-07 subset) describing this tool's input, in the shape Claude's tool-use API expects. */
    Map<String, Object> parameterSchema();

    /** @return anything JSON-serializable to hand back to the model as the tool result. Throw {@code ApiException} on failure. */
    Object execute(AgentToolContext context, JsonNode input);
}
