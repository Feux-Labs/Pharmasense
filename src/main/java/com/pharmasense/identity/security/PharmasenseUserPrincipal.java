package com.pharmasense.identity.security;

import com.pharmasense.identity.enums.UserRoleEnum;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal attached to every request's SecurityContext.
 * Built entirely from the access token's claims by {@link JwtAuthenticationFilter}
 * - no database lookup happens per request, which is what makes the API
 * horizontally scalable. The tradeoff: a role or status change only takes
 * effect once the user's current access token expires (at most
 * {@code pharmasense.jwt.access-token-ttl-minutes} later).
 *
 * @param impersonatedBy when set, this request is a super-admin acting as
 *                        this user via the admin impersonation flow; every
 *                        service that writes data should still record the
 *                        real actor for audit purposes using this field.
 */
public record PharmasenseUserPrincipal(
        UUID userId,
        UUID pharmacyId,
        String email,
        UserRoleEnum role,
        UUID impersonatedBy) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public boolean isSuperAdmin() {
        return role == UserRoleEnum.SUPER_ADMIN;
    }

    public boolean isImpersonated() {
        return impersonatedBy != null;
    }
}
