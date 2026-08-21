package com.pharmasense.sync.repository;

import com.pharmasense.sync.entity.SyncPushReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SyncPushReceiptRepository extends JpaRepository<SyncPushReceiptEntity, UUID> {

    Optional<SyncPushReceiptEntity> findByPharmacyIdAndClientOperationId(UUID pharmacyId, UUID clientOperationId);
}
