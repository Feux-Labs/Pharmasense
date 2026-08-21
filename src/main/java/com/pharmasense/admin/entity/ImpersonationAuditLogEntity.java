package com.pharmasense.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per "act as this user" grant a super-admin issues. This is the
 * audit trail for what is otherwise a very powerful capability - anyone
 * reviewing platform access can see exactly who impersonated whom and when.
 * The token minted from this grant carries {@code impersonatedBy} as a JWT
 * claim (see {@code JwtService}), so every subsequent request the
 * super-admin makes while impersonating is traceable back to this record.
 */
@Getter
@Setter
@Entity
@Table(name = "impersonation_audit_log")
public class ImpersonationAuditLogEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "super_admin_user_id", nullable = false, updatable = false)
    private UUID superAdminUserId;

    @Column(name = "target_user_id", nullable = false, updatable = false)
    private UUID targetUserId;

    @Column(name = "target_pharmacy_id", updatable = false)
    private UUID targetPharmacyId;

    @Column(updatable = false)
    private String reason;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
}
