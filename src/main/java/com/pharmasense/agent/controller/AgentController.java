package com.pharmasense.agent.controller;

import com.pharmasense.agent.dto.AgentChatRequest;
import com.pharmasense.agent.dto.AgentChatResponse;
import com.pharmasense.agent.service.AgentConversationService;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The "no technical know-how needed" surface - one endpoint, natural language in, actions taken, plain-language reply out. */
@Tag(name = "Agent")
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentConversationService agentConversationService;

    public AgentController(AgentConversationService agentConversationService) {
        this.agentConversationService = agentConversationService;
    }

    @PostMapping("/chat")
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'AGENT_USE')")
    public ApiResponse<AgentChatResponse> chat(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @Valid @RequestBody AgentChatRequest request) {
        AgentToolContext context = new AgentToolContext(principal.pharmacyId(), principal.userId(), principal.role());
        return ApiResponse.ok(agentConversationService.chat(context, request));
    }
}
