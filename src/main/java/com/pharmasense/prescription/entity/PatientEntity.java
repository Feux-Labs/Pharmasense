package com.pharmasense.prescription.entity;

import com.pharmasense.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "patients")
public class PatientEntity extends TenantScopedEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Free text: known allergies, chronic conditions, anything staff should see before filling a prescription. */
    private String notes;
}
