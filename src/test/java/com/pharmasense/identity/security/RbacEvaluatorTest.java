package com.pharmasense.identity.security;

import com.pharmasense.identity.enums.UserRoleEnum;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RbacEvaluatorTest {

    private final RbacEvaluator rbacEvaluator = new RbacEvaluator();

    @Test
    void ownerHasStaffManagePermission() {
        Authentication authentication = authenticationFor(UserRoleEnum.OWNER);
        assertThat(rbacEvaluator.hasPermission(authentication, "STAFF_MANAGE")).isTrue();
    }

    @Test
    void staffDoesNotHaveStaffManagePermission() {
        Authentication authentication = authenticationFor(UserRoleEnum.STAFF);
        assertThat(rbacEvaluator.hasPermission(authentication, "STAFF_MANAGE")).isFalse();
    }

    @Test
    void staffCanReadInventory() {
        Authentication authentication = authenticationFor(UserRoleEnum.STAFF);
        assertThat(rbacEvaluator.hasPermission(authentication, "INVENTORY_READ")).isTrue();
    }

    @Test
    void staffCannotDeleteInventory() {
        Authentication authentication = authenticationFor(UserRoleEnum.STAFF);
        assertThat(rbacEvaluator.hasPermission(authentication, "INVENTORY_DELETE")).isFalse();
    }

    @Test
    void superAdminBypassesEveryPermissionCheck() {
        Authentication authentication = authenticationFor(UserRoleEnum.SUPER_ADMIN);
        assertThat(rbacEvaluator.hasPermission(authentication, "STAFF_MANAGE")).isTrue();
        assertThat(rbacEvaluator.hasPermission(authentication, "INVENTORY_DELETE")).isTrue();
        assertThat(rbacEvaluator.isSuperAdmin(authentication)).isTrue();
    }

    @Test
    void unauthenticatedRequestHasNoPermission() {
        assertThat(rbacEvaluator.hasPermission(null, "INVENTORY_READ")).isFalse();
    }

    private Authentication authenticationFor(UserRoleEnum role) {
        PharmasenseUserPrincipal principal = new PharmasenseUserPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "user@example.com", role, null);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
