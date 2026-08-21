package com.pharmasense.identity.dto;

import com.pharmasense.identity.enums.UserRoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteStaffRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotNull UserRoleEnum role) {
}
