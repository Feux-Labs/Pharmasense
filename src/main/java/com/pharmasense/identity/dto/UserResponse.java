package com.pharmasense.identity.dto;

import com.pharmasense.identity.enums.UserRoleEnum;
import com.pharmasense.identity.enums.UserStatusEnum;

import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID pharmacyId,
        String email,
        String fullName,
        String avatarUrl,
        UserRoleEnum role,
        UserStatusEnum status) {
}
