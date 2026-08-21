package com.pharmasense.identity.security;

import com.pharmasense.identity.enums.PermissionEnum;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Backs every {@code @PreAuthorize("@rbacEvaluator.hasPermission(...)")}
 * check in the controller layer. Centralising the lookup here means the
 * role-to-permission mapping in {@link com.pharmasense.identity.enums.UserRoleEnum}
 * is the single source of truth - no permission string is ever duplicated
 * into a controller annotation.
 */
@Component
public class RbacEvaluator {

    public boolean hasPermission(Authentication authentication, String permissionName) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PharmasenseUserPrincipal principal)) {
            return false;
        }
        if (principal.isSuperAdmin()) {
            return true;
        }
        PermissionEnum permission = PermissionEnum.valueOf(permissionName);
        return principal.role().getPermissions().contains(permission);
    }

    public boolean isSuperAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof PharmasenseUserPrincipal principal
                && principal.isSuperAdmin();
    }
}
