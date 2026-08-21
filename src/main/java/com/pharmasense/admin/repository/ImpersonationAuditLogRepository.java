package com.pharmasense.admin.repository;

import com.pharmasense.admin.entity.ImpersonationAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImpersonationAuditLogRepository extends JpaRepository<ImpersonationAuditLogEntity, UUID> {

    Page<ImpersonationAuditLogEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
