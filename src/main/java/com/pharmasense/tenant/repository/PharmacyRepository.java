package com.pharmasense.tenant.repository;

import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PharmacyRepository extends JpaRepository<PharmacyEntity, UUID> {

    Page<PharmacyEntity> findAll(Pageable pageable);

    boolean existsByContactEmailIgnoreCase(String contactEmail);

    long countBySubscriptionStatus(PharmacySubscriptionStatusEnum subscriptionStatus);
}
