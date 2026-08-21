package com.pharmasense.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePharmacySetupRequest(
        @NotBlank @Size(max = 255) String pharmacyName,
        @Size(min = 3, max = 3) String currencyCode) {
}
