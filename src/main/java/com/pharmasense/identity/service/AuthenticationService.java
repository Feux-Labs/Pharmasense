package com.pharmasense.identity.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.dto.AuthResponse;
import com.pharmasense.identity.dto.UserResponse;
import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.enums.OtpPurpose;
import com.pharmasense.identity.mapper.UserAccountMapper;
import com.pharmasense.identity.security.JwtService;
import com.pharmasense.notification.service.EmailService;
import com.pharmasense.identity.config.OtpProperties;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.service.PharmacyService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates the email + OTP authentication flow: pharmacy signup, code
 * request, code verification (which mints tokens), refresh, and logout.
 * Google OAuth2 login is handled separately by
 * {@link com.pharmasense.identity.security.OAuth2AuthenticationSuccessHandler},
 * which calls the same {@link JwtService} / {@link RefreshTokenService} to
 * keep token issuance in one place.
 */
@Service
public class AuthenticationService {

    private final PharmacyService pharmacyService;
    private final UserAccountService userAccountService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserAccountMapper userAccountMapper;
    private final OtpProperties otpProperties;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(
            PharmacyService pharmacyService,
            UserAccountService userAccountService,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserAccountMapper userAccountMapper,
            OtpProperties otpProperties,
            PasswordEncoder passwordEncoder) {
        this.pharmacyService = pharmacyService;
        this.userAccountService = userAccountService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userAccountMapper = userAccountMapper;
        this.otpProperties = otpProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse registerPharmacyAndOwner(
            String pharmacyName, String ownerFullName, String ownerEmail, String password, String currencyCode, String deviceLabel) {
        PharmacyEntity pharmacy = pharmacyService.registerPharmacy(pharmacyName, ownerEmail, null, currencyCode);
        UserAccountEntity owner = userAccountService.createOwnerAccount(
                pharmacy.getId(), ownerEmail, ownerFullName, passwordEncoder.encode(password));
        return buildAuthResponse(owner, deviceLabel);
    }

    @Transactional
    public AuthResponse login(String email, String password, String deviceLabel) {
        UserAccountEntity user = userAccountService.getByEmail(email);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTHENTICATION_FAILED, "Incorrect email or password");
        }
        userAccountService.markLoggedIn(user);
        return buildAuthResponse(user, deviceLabel);
    }

    public void sendOtpCode(String email) {
        UserAccountEntity user = userAccountService.getByEmail(email);
        String code = otpService.generateAndStore(email, OtpPurpose.LOGIN);
        emailService.sendOtpCode(email, user.getFullName(), code, otpProperties.ttlMinutes());
    }

    @Transactional
    public AuthResponse verifyOtpAndIssueTokens(String email, String code, String deviceLabel) {
        otpService.verify(email, code, OtpPurpose.LOGIN);
        UserAccountEntity user = userAccountService.getByEmail(email);
        userAccountService.markLoggedIn(user);
        return buildAuthResponse(user, deviceLabel);
    }

    /**
     * Silently no-ops for an unknown email so this can't be used to enumerate
     * registered accounts - the controller always returns the same generic
     * message regardless of whether a code was actually sent.
     */
    public void sendPasswordResetCode(String email) {
        userAccountService.findByEmailOrNull(email).ifPresent(user -> {
            String code = otpService.generateAndStore(email, OtpPurpose.PASSWORD_RESET);
            emailService.sendPasswordResetCode(email, user.getFullName(), code, otpProperties.ttlMinutes());
        });
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        otpService.verify(email, code, OtpPurpose.PASSWORD_RESET);
        UserAccountEntity user = userAccountService.getByEmail(email);
        userAccountService.updatePasswordHash(user.getId(), passwordEncoder.encode(newPassword));
        // A password reset means the old one may have been compromised -
        // force every existing session to re-authenticate with the new one.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String deviceLabel) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken, deviceLabel);
        UserAccountEntity user = userAccountService.getById(rotation.userId());
        String accessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(accessToken, rotation.newRawToken(), jwtService.accessTokenTtlMinutes() * 60L, toUserResponse(user));
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    /**
     * A Google sign-in creates a {@code UserAccountEntity} with no pharmacy
     * attached (see {@link UserAccountService#findOrCreateGoogleUser}) since
     * we don't yet know which pharmacy the person is signing up for. The
     * frontend should route a user whose profile comes back with
     * {@code pharmacyId == null} to a short "name your pharmacy" step that
     * calls this to finish onboarding, then swap in the freshly-minted
     * tokens this returns (the previous access token has a stale, null
     * pharmacyId claim).
     */
    @Transactional
    public AuthResponse completePharmacySetupForGoogleUser(UUID userId, String pharmacyName, String currencyCode) {
        UserAccountEntity user = userAccountService.getById(userId);
        if (user.getPharmacyId() != null) {
            return buildAuthResponse(user, null);
        }
        PharmacyEntity pharmacy = pharmacyService.registerPharmacy(pharmacyName, user.getEmail(), null, currencyCode);
        user.setPharmacyId(pharmacy.getId());
        UserAccountEntity saved = userAccountService.save(user);
        return buildAuthResponse(saved, null);
    }

    private AuthResponse buildAuthResponse(UserAccountEntity user, String deviceLabel) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId(), deviceLabel);
        return new AuthResponse(accessToken, refreshToken, jwtService.accessTokenTtlMinutes() * 60L, toUserResponse(user));
    }

    private UserResponse toUserResponse(UserAccountEntity user) {
        return userAccountMapper.toResponse(user);
    }
}
