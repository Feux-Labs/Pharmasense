package com.pharmasense.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterPharmacyRequest(
        @NotBlank @Size(max = 255) String pharmacyName,
        @NotBlank @Size(max = 255) String ownerFullName,
        @NotBlank @Email String ownerEmail,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(min = 3, max = 3) String currencyCode) {
}
