package com.pharmasense.prescription.dto;

import com.pharmasense.prescription.enums.PrescriptionStatusEnum;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        UUID patientId,
        String patientName,
        String prescribingDoctor,
        PrescriptionStatusEnum status,
        String notes,
        UUID filledByUserId,
        Instant filledAt,
        List<PrescriptionItemResponse> items,
        Instant createdAt) {
}
