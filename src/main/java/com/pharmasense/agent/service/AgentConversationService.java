package com.pharmasense.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pharmasense.agent.client.GroqApiClient;
import com.pharmasense.agent.client.GroqContentBlock;
import com.pharmasense.agent.client.GroqMessageResponse;
import com.pharmasense.agent.config.AgentProperties;
import com.pharmasense.agent.dto.AgentActionSummary;
import com.pharmasense.agent.dto.AgentChatRequest;
import com.pharmasense.agent.dto.AgentChatResponse;
import com.pharmasense.agent.dto.AgentMessageDto;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.agent.tool.AgentToolRegistry;
import com.pharmasense.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the "agent loop": send the conversation to the model (a Llama
 * checkpoint hosted on Groq), and whenever it asks to use a tool, run that
 * tool through {@link AgentToolRegistry} and feed the result back, repeating
 * until the model answers in plain text (or the iteration cap is hit, which
 * protects against a runaway tool-calling loop). This is what lets a
 * non-technical user type "add 50 paracetamol at 1200 each" and have it
 * actually happen, instead of just getting an explanation of how they'd do
 * it themselves.
 */
@Service
public class AgentConversationService {

    private static final Logger log = LoggerFactory.getLogger(AgentConversationService.class);

    private static final String SYSTEM_PROMPT = """
            You are the Pharmasense assistant, built into a pharmacy management platform. You help pharmacy \
            staff manage inventory, check stock, track prescriptions, and understand what's happening in their \
            store - entirely through conversation, with no technical knowledge required on their part.

            Use the tools available to you to actually perform actions and look up real data - never guess or \
            make up numbers. If a request is ambiguous (e.g. which product they mean), ask a short clarifying \
            question instead of assuming. Keep replies concise and concrete: confirm what you did or found, \
            with the actual numbers, in plain language a busy pharmacist can read in a few seconds.
            """;

    private final GroqApiClient groqApiClient;
    private final AgentToolRegistry agentToolRegistry;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    public AgentConversationService(
            GroqApiClient groqApiClient,
            AgentToolRegistry agentToolRegistry,
            AgentProperties agentProperties,
            ObjectMapper objectMapper) {
        this.groqApiClient = groqApiClient;
        this.agentToolRegistry = agentToolRegistry;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(AgentToolContext context, AgentChatRequest request) {
        List<JsonNode> messages = new ArrayList<>();
        messages.add(textMessage("system", SYSTEM_PROMPT));
        if (request.history() != null) {
            for (AgentMessageDto turn : request.history()) {
                messages.add(textMessage(turn.role(), turn.content()));
            }
        }
        messages.add(textMessage("user", request.message()));

        List<JsonNode> toolDefinitions = agentToolRegistry.buildToolDefinitionsFor(context);
        List<AgentActionSummary> actionsPerformed = new ArrayList<>();

        for (int iteration = 0; iteration < agentProperties.maxToolIterations(); iteration++) {
            GroqMessageResponse response = groqApiClient.sendMessage(messages, toolDefinitions);

            if (!response.requestedToolUse()) {
                return new AgentChatResponse(response.concatenatedText(), actionsPerformed);
            }

            messages.add(assistantMessageFrom(response.content()));
            messages.addAll(toolResultMessagesFor(context, response.content(), actionsPerformed));
        }

        log.warn("Agent conversation for pharmacy {} exceeded {} tool iterations", context.pharmacyId(), agentProperties.maxToolIterations());
        return new AgentChatResponse(
                "I performed several steps but couldn't finish - could you rephrase or break that into smaller requests?",
                actionsPerformed);
    }

    private JsonNode textMessage(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private JsonNode assistantMessageFrom(List<GroqContentBlock> blocks) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "assistant");

        String text = blocks.stream()
                .filter(GroqContentBlock.Text.class::isInstance)
                .map(block -> ((GroqContentBlock.Text) block).text())
                .findFirst()
                .orElse(null);
        if (text != null) {
            message.put("content", text);
        } else {
            message.putNull("content");
        }

        List<GroqContentBlock.ToolCall> toolCalls = blocks.stream()
                .filter(GroqContentBlock.ToolCall.class::isInstance)
                .map(GroqContentBlock.ToolCall.class::cast)
                .toList();

        if (!toolCalls.isEmpty()) {
            ArrayNode toolCallsArray = message.putArray("tool_calls");
            for (GroqContentBlock.ToolCall toolCall : toolCalls) {
                ObjectNode toolCallNode = objectMapper.createObjectNode();
                toolCallNode.put("id", toolCall.id());
                toolCallNode.put("type", "function");
                ObjectNode function = toolCallNode.putObject("function");
                function.put("name", toolCall.name());
                function.put("arguments", toolCall.arguments().toString());
                toolCallsArray.add(toolCallNode);
            }
        }
        return message;
    }

    /** OpenAI-style tool results are one message per call, not bundled content blocks. */
    private List<JsonNode> toolResultMessagesFor(AgentToolContext context, List<GroqContentBlock> blocks, List<AgentActionSummary> actionsPerformed) {
        List<JsonNode> results = new ArrayList<>();
        for (GroqContentBlock block : blocks) {
            if (block instanceof GroqContentBlock.ToolCall toolCall) {
                results.add(executeToolAndBuildResultMessage(context, toolCall, actionsPerformed));
            }
        }
        return results;
    }

    private ObjectNode executeToolAndBuildResultMessage(AgentToolContext context, GroqContentBlock.ToolCall toolCall, List<AgentActionSummary> actionsPerformed) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "tool");
        message.put("tool_call_id", toolCall.id());

        try {
            Object result = agentToolRegistry.dispatch(context, toolCall.name(), toolCall.arguments());
            message.put("content", objectMapper.writeValueAsString(result));
            actionsPerformed.add(new AgentActionSummary(toolCall.name(), true, null));
        } catch (ApiException apiException) {
            message.put("content", apiException.getMessage());
            actionsPerformed.add(new AgentActionSummary(toolCall.name(), false, apiException.getMessage()));
        } catch (Exception unexpected) {
            log.error("Agent tool '{}' failed unexpectedly", toolCall.name(), unexpected);
            message.put("content", "This action could not be completed due to an internal error.");
            actionsPerformed.add(new AgentActionSummary(toolCall.name(), false, "Internal error"));
        }
        return message;
    }
}
