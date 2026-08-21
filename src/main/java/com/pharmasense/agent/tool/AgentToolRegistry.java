package com.pharmasense.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects every {@link AgentTool} bean and does the two things
 * {@link com.pharmasense.agent.service.AgentConversationService} needs:
 * build the tool-definition list to offer the model (already filtered to
 * what this user's role permits, so it's never even offered an action it
 * can't take), and dispatch a requested tool call to the right
 * implementation with a second permission check before it runs.
 */
@Component
public class AgentToolRegistry {

    private final List<AgentTool> tools;
    private final ObjectMapper objectMapper;

    public AgentToolRegistry(List<AgentTool> tools, ObjectMapper objectMapper) {
        this.tools = tools;
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> buildToolDefinitionsFor(AgentToolContext context) {
        return tools.stream()
                .filter(tool -> isPermitted(tool, context))
                .map(this::toOpenAiToolDefinition)
                .toList();
    }

    public Object dispatch(AgentToolContext context, String toolName, JsonNode input) {
        AgentTool tool = tools.stream()
                .filter(candidate -> candidate.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.AGENT_TOOL_EXECUTION_FAILED, "Unknown tool: " + toolName));

        if (!isPermitted(tool, context)) {
            throw new ApiException(ErrorCode.AGENT_PERMISSION_DENIED, "You don't have permission to " + tool.description().toLowerCase());
        }

        return tool.execute(context, input);
    }

    private boolean isPermitted(AgentTool tool, AgentToolContext context) {
        if (tool.requiredPermission() == null) {
            return true;
        }
        if (context.role() == com.pharmasense.identity.enums.UserRoleEnum.SUPER_ADMIN) {
            return true;
        }
        return context.role().getPermissions().contains(tool.requiredPermission());
    }

    private JsonNode toOpenAiToolDefinition(AgentTool tool) {
        ObjectNode function = objectMapper.createObjectNode();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.set("parameters", objectMapper.valueToTree(tool.parameterSchema()));

        ObjectNode definition = objectMapper.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    public Optional<AgentTool> findByName(String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst();
    }

    Map<String, Object> schemaOf(String toolName) {
        return findByName(toolName).map(AgentTool::parameterSchema).orElse(Map.of());
    }
}
