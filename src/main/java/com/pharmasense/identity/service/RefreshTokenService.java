package com.pharmasense.identity.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.config.JwtProperties;
import com.pharmasense.identity.entity.RefreshTokenEntity;
import com.pharmasense.identity.repository.RefreshTokenRepository;
import com.pharmasense.identity.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Issues, rotates, and revokes refresh tokens. Rotation-on-use means every
 * refresh call invalidates the token it consumed and hands back a new one;
 * if a revoked (already-rotated) token is ever presented again, that's a
 * strong signal of token theft, so we revoke the entire chain for that user.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public String issue(UUID userId, String deviceLabel) {
        String rawToken = tokenHasher.generateOpaqueToken();
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(tokenHasher.sha256(rawToken));
        entity.setDeviceLabel(deviceLabel);
        entity.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtlDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /** Validates the presented token, revokes it, and issues its replacement in one transaction. */
    @Transactional
    public RotationResult rotate(String rawToken, String deviceLabel) {
        String hash = tokenHasher.sha256(rawToken);
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token is invalid"));

        if (existing.isRevoked()) {
            log.warn("Refresh token reuse detected for user {} - revoking all active sessions", existing.getUserId());
            refreshTokenRepository.revokeAllActiveForUser(existing.getUserId(), Instant.now());
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token has already been used; all sessions revoked");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token has expired");
        }

        String newRawToken = tokenHasher.generateOpaqueToken();
        RefreshTokenEntity replacement = new RefreshTokenEntity();
        replacement.setUserId(existing.getUserId());
        replacement.setTokenHash(tokenHasher.sha256(newRawToken));
        replacement.setDeviceLabel(deviceLabel);
        replacement.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtlDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(replacement);

        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
        existing.setRotatedToTokenId(replacement.getId());
        refreshTokenRepository.save(existing);

        return new RotationResult(existing.getUserId(), newRawToken);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    public record RotationResult(UUID userId, String newRawToken) {
    }
}
