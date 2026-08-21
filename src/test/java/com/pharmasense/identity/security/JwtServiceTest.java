package com.pharmasense.identity.security;

import com.pharmasense.identity.config.JwtProperties;
import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.enums.AuthProviderEnum;
import com.pharmasense.identity.enums.UserRoleEnum;
import com.pharmasense.identity.enums.UserStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtProperties jwtProperties = new JwtProperties(
            "unit-test-only-secret-key-that-is-long-enough-for-hs256", "pharmasense-test", 15, 30);
    private final JwtService jwtService = new JwtService(jwtProperties);

    @Test
    void generatedTokenRoundTripsWithMatchingClaims() {
        UserAccountEntity user = ownerUser();

        String token = jwtService.generateAccessToken(user);
        Optional<PharmasenseUserPrincipal> parsed = jwtService.parseAndValidate(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().userId()).isEqualTo(user.getId());
        assertThat(parsed.get().pharmacyId()).isEqualTo(user.getPharmacyId());
        assertThat(parsed.get().email()).isEqualTo(user.getEmail());
        assertThat(parsed.get().role()).isEqualTo(UserRoleEnum.OWNER);
        assertThat(parsed.get().impersonatedBy()).isNull();
    }

    @Test
    void impersonationClaimRoundTrips() {
        UserAccountEntity user = ownerUser();
        UUID superAdminId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, superAdminId);
        Optional<PharmasenseUserPrincipal> parsed = jwtService.parseAndValidate(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().impersonatedBy()).isEqualTo(superAdminId);
        assertThat(parsed.get().isImpersonated()).isTrue();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        UserAccountEntity user = ownerUser();
        String token = jwtService.generateAccessToken(user);

        JwtService otherJwtService = new JwtService(new JwtProperties(
                "a-completely-different-unit-test-secret-key-value", "pharmasense-test", 15, 30));

        assertThat(otherJwtService.parseAndValidate(token)).isEmpty();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(jwtService.parseAndValidate("not-a-real-token")).isEmpty();
    }

    private UserAccountEntity ownerUser() {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(UUID.randomUUID());
        user.setPharmacyId(UUID.randomUUID());
        user.setEmail("owner@example.com");
        user.setFullName("Test Owner");
        user.setRole(UserRoleEnum.OWNER);
        user.setAuthProvider(AuthProviderEnum.LOCAL_OTP);
        user.setStatus(UserStatusEnum.ACTIVE);
        return user;
    }
}
