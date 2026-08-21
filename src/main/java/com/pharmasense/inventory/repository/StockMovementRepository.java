package com.pharmasense.inventory.repository;

import com.pharmasense.inventory.entity.StockMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {

    Page<StockMovementEntity> findByInventoryItemIdOrderByCreatedAtDesc(UUID inventoryItemId, Pageable pageable);

    Page<StockMovementEntity> findByPharmacyIdAndCreatedAtBetween(UUID pharmacyId, Instant from, Instant to, Pageable pageable);
}
