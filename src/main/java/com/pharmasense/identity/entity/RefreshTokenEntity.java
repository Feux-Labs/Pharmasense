package com.pharmasense.identity.entity;

import com.pharmasense.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh tokens are opaque random strings, not JWTs - we store only a SHA-256
 * hash of the token, never the raw value, so a leaked database dump can't be
 * replayed. {@code rotatedToTokenId} chains a token to the one that replaced
 * it, so reuse of an already-rotated token (a strong signal of theft) can be
 * detected and the whole chain revoked.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_to_token_id")
    private UUID rotatedToTokenId;
}
