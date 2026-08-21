package com.pharmasense.inventory.repository;

import com.pharmasense.inventory.entity.InventoryBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatchEntity, UUID> {

    Optional<InventoryBatchEntity> findByIdAndPharmacyId(UUID id, UUID pharmacyId);

    List<InventoryBatchEntity> findByPharmacyId(UUID pharmacyId);

    List<InventoryBatchEntity> findByInventoryItemIdOrderByExpiryDateAsc(UUID inventoryItemId);

    /** FEFO: batches with stock remaining, nearest expiry first. Batches with no expiry date sort last. */
    List<InventoryBatchEntity> findByInventoryItemIdAndQuantityOnHandGreaterThanOrderByExpiryDateAscNullsLast(
            UUID inventoryItemId, int minQuantity);

    List<InventoryBatchEntity> findByPharmacyIdAndExpiryDateBetween(UUID pharmacyId, LocalDate from, LocalDate to);

    List<InventoryBatchEntity> findByPharmacyIdAndExpiryDateLessThan(UUID pharmacyId, LocalDate before);

    Optional<InventoryBatchEntity> findByQrCode(String qrCode);
}
