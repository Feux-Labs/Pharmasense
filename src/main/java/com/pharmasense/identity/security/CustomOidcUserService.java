package com.pharmasense.identity.security;

import com.pharmasense.identity.service.UserAccountService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Google's OIDC scope ("openid profile email") means Spring Security routes
 * the login through the OIDC user pipeline rather than the plain OAuth2 one.
 * On every successful Google login we find-or-create the matching
 * {@code UserAccountEntity} here, before {@link OAuth2AuthenticationSuccessHandler}
 * runs and looks that account back up by email to mint our own JWTs.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserAccountService userAccountService;

    public CustomOidcUserService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        String fullName = oidcUser.getFullName() != null ? oidcUser.getFullName() : email;
        String subject = oidcUser.getSubject();

        userAccountService.findOrCreateGoogleUser(email, fullName, subject);

        return oidcUser;
    }
}
