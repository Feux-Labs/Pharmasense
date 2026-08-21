package com.pharmasense.prescription.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        String notes,
        Instant createdAt) {
}
