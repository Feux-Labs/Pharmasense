package com.pharmasense.prescription.repository;

import com.pharmasense.prescription.entity.PrescriptionEntity;
import com.pharmasense.prescription.enums.PrescriptionStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<PrescriptionEntity, UUID> {

    Optional<PrescriptionEntity> findByIdAndPharmacyId(UUID id, UUID pharmacyId);

    Page<PrescriptionEntity> findByPharmacyId(UUID pharmacyId, Pageable pageable);

    Page<PrescriptionEntity> findByPharmacyIdAndStatus(UUID pharmacyId, PrescriptionStatusEnum status, Pageable pageable);

    Page<PrescriptionEntity> findByPharmacyIdAndPatientId(UUID pharmacyId, UUID patientId, Pageable pageable);
}
