package com.pharmasense.identity.repository;

import com.pharmasense.identity.entity.UserAccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);

    Optional<UserAccountEntity> findByGoogleSubjectId(String googleSubjectId);

    boolean existsByEmailIgnoreCase(String email);

    List<UserAccountEntity> findByPharmacyId(UUID pharmacyId);

    Page<UserAccountEntity> findByPharmacyId(UUID pharmacyId, Pageable pageable);

    long countByPharmacyId(UUID pharmacyId);
}
