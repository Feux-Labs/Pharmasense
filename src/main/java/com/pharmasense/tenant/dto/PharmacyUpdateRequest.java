package com.pharmasense.tenant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PharmacyUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        String contactPhone,
        String addressLine,
        String city,
        String country,
        String taxRegistrationNumber,
        @Size(min = 3, max = 3) String currencyCode,
        String timezone,
        @Min(0) Integer lowStockThresholdDefault,
        @Min(0) Integer expiryWarningDaysDefault) {
}
