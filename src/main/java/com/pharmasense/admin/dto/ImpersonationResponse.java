package com.pharmasense.admin.dto;

import com.pharmasense.identity.dto.UserResponse;

public record ImpersonationResponse(
        String accessToken,
        long expiresInSeconds,
        UserResponse impersonatedUser) {
}
