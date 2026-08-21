package com.pharmasense.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Base for every entity that belongs to exactly one pharmacy. Every
 * repository query against a subclass must filter by {@code pharmacyId} -
 * there is no cross-tenant read path except through the super-admin module,
 * which uses its own explicitly-audited queries.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantScopedEntity extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID pharmacyId;
}
