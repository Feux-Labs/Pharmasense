package com.pharmasense.prescription.repository;

import com.pharmasense.prescription.entity.PrescriptionItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItemEntity, UUID> {

    List<PrescriptionItemEntity> findByPrescriptionId(UUID prescriptionId);
}
