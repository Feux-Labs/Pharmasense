package com.pharmasense.inventory.repository;

import com.pharmasense.inventory.entity.InventoryItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, UUID> {

    Optional<InventoryItemEntity> findByIdAndPharmacyId(UUID id, UUID pharmacyId);

    Page<InventoryItemEntity> findByPharmacyIdAndActiveTrue(UUID pharmacyId, Pageable pageable);

    Page<InventoryItemEntity> findByPharmacyIdAndActiveTrueAndNameContainingIgnoreCase(
            UUID pharmacyId, String nameFragment, Pageable pageable);

    Optional<InventoryItemEntity> findByQrCode(String qrCode);

    List<InventoryItemEntity> findByPharmacyIdAndIdIn(UUID pharmacyId, List<UUID> ids);

    long countByPharmacyIdAndActiveTrue(UUID pharmacyId);
}
