package com.pharmasense.identity.security;

import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.service.RefreshTokenService;
import com.pharmasense.identity.service.UserAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Google login is a browser redirect flow, not a JSON API call - so instead
 * of returning a body, we mint tokens and hand them to the SPA via a
 * redirect query string. The frontend's /oauth2/callback route reads them
 * from the URL and stores them exactly like it would an OTP-login response.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserAccountService userAccountService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final String frontendBaseUrl;

    public OAuth2AuthenticationSuccessHandler(
            UserAccountService userAccountService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Value("${pharmasense.frontend.base-url}") String frontendBaseUrl) {
        this.userAccountService = userAccountService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        UserAccountEntity user = userAccountService.getByEmail(oidcUser.getEmail());
        userAccountService.markLoggedIn(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId(), "google-oauth2");

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl + "/oauth2/callback")
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("needsPharmacySetup", user.getPharmacyId() == null)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
