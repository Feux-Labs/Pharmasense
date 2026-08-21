package com.pharmasense.prescription.dto;

import com.pharmasense.prescription.enums.PrescriptionStatusEnum;
import jakarta.validation.constraints.NotNull;

public record PrescriptionStatusUpdateRequest(
        @NotNull PrescriptionStatusEnum status) {
}
