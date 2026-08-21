package com.pharmasense.identity.entity;

import com.pharmasense.common.domain.AuditableEntity;
import com.pharmasense.identity.enums.AuthProviderEnum;
import com.pharmasense.identity.enums.UserRoleEnum;
import com.pharmasense.identity.enums.UserStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A person who can sign in. {@code pharmacyId} is null only for
 * {@link UserRoleEnum#SUPER_ADMIN} accounts, which operate across every
 * tenant through the admin module rather than belonging to one.
 */
@Getter
@Setter
@Entity
@Table(name = "user_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_accounts_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_user_accounts_google_subject_id", columnNames = "google_subject_id")
})
public class UserAccountEntity extends AuditableEntity {

    @Column(name = "pharmacy_id")
    private UUID pharmacyId;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRoleEnum role;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 32)
    private AuthProviderEnum authProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatusEnum status;

    @Column(name = "google_subject_id")
    private String googleSubjectId;

    /** Null for accounts that only ever signed in via Google or a not-yet-set-up OTP account. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
