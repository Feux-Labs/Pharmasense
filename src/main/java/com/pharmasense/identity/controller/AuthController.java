package com.pharmasense.identity.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.dto.AuthResponse;
import com.pharmasense.identity.dto.CompletePharmacySetupRequest;
import com.pharmasense.identity.dto.ForgotPasswordRequest;
import com.pharmasense.identity.dto.LoginRequest;
import com.pharmasense.identity.dto.OtpRequestDto;
import com.pharmasense.identity.dto.OtpVerifyRequest;
import com.pharmasense.identity.dto.RefreshTokenRequest;
import com.pharmasense.identity.dto.RegisterPharmacyRequest;
import com.pharmasense.identity.dto.ResetPasswordRequest;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.identity.service.AuthenticationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Google OAuth2 login is not here - it's a
 * browser redirect flow started at {@code GET /oauth2/authorization/google}
 * and finished by {@link com.pharmasense.identity.security.OAuth2AuthenticationSuccessHandler},
 * both wired up by Spring Security in {@link com.pharmasense.identity.security.SecurityConfig}.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> signup(@Valid @RequestBody RegisterPharmacyRequest request) {
        AuthResponse authResponse = authenticationService.registerPharmacyAndOwner(
                request.pharmacyName(), request.ownerFullName(), request.ownerEmail(), request.password(),
                request.currencyCode(), null);
        return ApiResponse.ok(authResponse, "Pharmacy created");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authenticationService.login(request.email(), request.password(), request.deviceLabel());
        return ApiResponse.ok(authResponse);
    }

    @PostMapping("/otp/request")
    public ApiResponse<Void> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        authenticationService.sendOtpCode(request.email());
        return ApiResponse.ok(null, "If an account exists for this email, a code has been sent.");
    }

    @PostMapping("/otp/verify")
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AuthResponse authResponse = authenticationService.verifyOtpAndIssueTokens(
                request.email(), request.code(), request.deviceLabel());
        return ApiResponse.ok(authResponse);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.sendPasswordResetCode(request.email());
        return ApiResponse.ok(null, "If an account exists for this email, a reset code has been sent.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request.email(), request.code(), request.newPassword());
        return ApiResponse.ok(null, "Password reset. Please sign in with your new password.");
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authenticationService.refresh(request.refreshToken(), request.deviceLabel());
        return ApiResponse.ok(authResponse);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal PharmasenseUserPrincipal principal) {
        authenticationService.logout(principal.userId());
        return ApiResponse.ok(null, "Logged out");
    }

    @PostMapping("/complete-pharmacy-setup")
    public ApiResponse<AuthResponse> completePharmacySetup(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @Valid @RequestBody CompletePharmacySetupRequest request) {
        AuthResponse authResponse = authenticationService.completePharmacySetupForGoogleUser(
                principal.userId(), request.pharmacyName(), request.currencyCode());
        return ApiResponse.ok(authResponse, "Pharmacy created");
    }
}
