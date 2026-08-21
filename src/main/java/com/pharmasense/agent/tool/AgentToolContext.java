package com.pharmasense.agent.tool;

import com.pharmasense.identity.enums.UserRoleEnum;

import java.util.UUID;

/**
 * What a tool is allowed to know about who's asking. Tools must always scope
 * their work to {@code pharmacyId} from here - never to anything the model
 * might echo back from the conversation - so a prompt-injected or
 * hallucinated pharmacy id in the chat text can never cause a cross-tenant
 * read or write.
 */
public record AgentToolContext(UUID pharmacyId, UUID userId, UserRoleEnum role) {
}
