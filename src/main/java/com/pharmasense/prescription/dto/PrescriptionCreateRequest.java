package com.pharmasense.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PrescriptionCreateRequest(
        @NotNull UUID patientId,
        String prescribingDoctor,
        String notes,
        @NotEmpty @Valid List<PrescriptionItemRequest> items) {
}
