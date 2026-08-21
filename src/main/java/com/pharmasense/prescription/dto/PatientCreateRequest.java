package com.pharmasense.prescription.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record PatientCreateRequest(
        @NotBlank String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        String notes) {
}
