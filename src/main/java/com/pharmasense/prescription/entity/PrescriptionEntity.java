package com.pharmasense.prescription.entity;

import com.pharmasense.common.domain.TenantScopedEntity;
import com.pharmasense.prescription.enums.PrescriptionStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "prescriptions")
public class PrescriptionEntity extends TenantScopedEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "prescribing_doctor")
    private String prescribingDoctor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PrescriptionStatusEnum status = PrescriptionStatusEnum.PENDING;

    private String notes;

    @Column(name = "filled_by_user_id")
    private UUID filledByUserId;

    @Column(name = "filled_at")
    private Instant filledAt;
}
