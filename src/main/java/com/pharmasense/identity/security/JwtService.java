package com.pharmasense.identity.security;

import com.pharmasense.identity.config.JwtProperties;
import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.enums.UserRoleEnum;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies short-lived access tokens. Refresh tokens are handled
 * separately (see {@link TokenHasher} + {@code RefreshTokenService}) because
 * they're opaque and revocable, not JWTs.
 */
@Service
public class JwtService {

    private static final String CLAIM_PHARMACY_ID = "pharmacyId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_IMPERSONATED_BY = "impersonatedBy";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserAccountEntity user) {
        return generateAccessToken(user, null, jwtProperties.accessTokenTtlMinutes());
    }

    /** @param impersonatedBy the super-admin user id, when this token was minted via admin impersonation */
    public String generateAccessToken(UserAccountEntity user, UUID impersonatedBy) {
        return generateAccessToken(user, impersonatedBy, jwtProperties.accessTokenTtlMinutes());
    }

    public String generateAccessToken(UserAccountEntity user, UUID impersonatedBy, int ttlMinutes) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttlMinutes, ChronoUnit.MINUTES);

        var builder = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_PHARMACY_ID, user.getPharmacyId() != null ? user.getPharmacyId().toString() : null)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (impersonatedBy != null) {
            builder.claim(CLAIM_IMPERSONATED_BY, impersonatedBy.toString());
        }

        return builder.signWith(signingKey).compact();
    }

    public Optional<PharmasenseUserPrincipal> parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String pharmacyIdClaim = claims.get(CLAIM_PHARMACY_ID, String.class);
            String impersonatedByClaim = claims.get(CLAIM_IMPERSONATED_BY, String.class);

            return Optional.of(new PharmasenseUserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    pharmacyIdClaim != null ? UUID.fromString(pharmacyIdClaim) : null,
                    claims.get(CLAIM_EMAIL, String.class),
                    UserRoleEnum.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    impersonatedByClaim != null ? UUID.fromString(impersonatedByClaim) : null));
        } catch (JwtException | IllegalArgumentException expiredOrMalformed) {
            return Optional.empty();
        }
    }

    public int accessTokenTtlMinutes() {
        return jwtProperties.accessTokenTtlMinutes();
    }

    public int refreshTokenTtlDays() {
        return jwtProperties.refreshTokenTtlDays();
    }
}
