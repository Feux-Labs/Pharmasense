package com.pharmasense.sync.repository;

import com.pharmasense.sync.entity.SyncChangeLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SyncChangeLogRepository extends JpaRepository<SyncChangeLogEntity, UUID> {

    List<SyncChangeLogEntity> findByPharmacyIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            UUID pharmacyId, long cursor, Pageable pageable);

    @Query("select max(s.sequenceNumber) from SyncChangeLogEntity s where s.pharmacyId = :pharmacyId")
    Long findMaxSequenceNumberByPharmacyId(@Param("pharmacyId") UUID pharmacyId);
}
