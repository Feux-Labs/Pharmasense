package com.pharmasense.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pharmasense.agent.config.AgentProperties;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around Groq's chat completions API
 * (https://console.groq.com/docs/api-reference#chat-create), which speaks
 * the same wire format as OpenAI's function-calling contract - Groq just
 * hosts the model (a Meta Llama checkpoint) and serves it fast.
 * {@link com.pharmasense.agent.service.AgentConversationService} only ever
 * needs this one endpoint, so a full SDK would be overkill.
 */
@Component
public class GroqApiClient {

    private static final Logger log = LoggerFactory.getLogger(GroqApiClient.class);
    private static final String CHAT_COMPLETIONS_PATH = "/openai/v1/chat/completions";

    private final WebClient groqWebClient;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    public GroqApiClient(WebClient groqWebClient, AgentProperties agentProperties, ObjectMapper objectMapper) {
        this.groqWebClient = groqWebClient;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
    }

    public GroqMessageResponse sendMessage(List<JsonNode> messages, List<JsonNode> toolDefinitions) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", agentProperties.model());
        requestBody.put("max_tokens", agentProperties.maxOutputTokens());

        ArrayNode messagesArray = requestBody.putArray("messages");
        messages.forEach(messagesArray::add);

        if (!toolDefinitions.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            toolDefinitions.forEach(toolsArray::add);
        }

        try {
            JsonNode response = groqWebClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));

            return parseResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Groq API call failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(ErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "The assistant is temporarily unavailable. Please try again.", e);
        }
    }

    private GroqMessageResponse parseResponse(JsonNode response) {
        JsonNode choice = response == null ? null : response.path("choices").path(0);
        if (choice == null || choice.isMissingNode()) {
            throw new ApiException(ErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "The assistant returned an empty response");
        }

        String finishReason = choice.path("finish_reason").asText(null);
        JsonNode message = choice.path("message");
        List<GroqContentBlock> blocks = new ArrayList<>();

        String text = message.path("content").asText(null);
        if (text != null && !text.isBlank()) {
            blocks.add(new GroqContentBlock.Text(text));
        }

        for (JsonNode toolCall : message.path("tool_calls")) {
            String id = toolCall.path("id").asText();
            JsonNode function = toolCall.path("function");
            String name = function.path("name").asText();
            blocks.add(new GroqContentBlock.ToolCall(id, name, parseArguments(function.path("arguments").asText("{}"))));
        }

        return new GroqMessageResponse(finishReason, blocks);
    }

    private JsonNode parseArguments(String rawArguments) {
        try {
            return objectMapper.readTree(rawArguments);
        } catch (JsonProcessingException e) {
            log.warn("Groq returned malformed tool-call arguments: {}", rawArguments);
            return objectMapper.createObjectNode();
        }
    }
}
