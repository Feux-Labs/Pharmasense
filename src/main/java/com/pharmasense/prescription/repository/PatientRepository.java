package com.pharmasense.prescription.repository;

import com.pharmasense.prescription.entity.PatientEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<PatientEntity, UUID> {

    Optional<PatientEntity> findByIdAndPharmacyId(UUID id, UUID pharmacyId);

    Page<PatientEntity> findByPharmacyIdAndFullNameContainingIgnoreCase(UUID pharmacyId, String nameFragment, Pageable pageable);

    Page<PatientEntity> findByPharmacyId(UUID pharmacyId, Pageable pageable);
}
