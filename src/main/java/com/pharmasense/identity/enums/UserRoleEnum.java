package com.pharmasense.identity.enums;

import java.util.Set;

import static com.pharmasense.identity.enums.PermissionEnum.AGENT_USE;
import static com.pharmasense.identity.enums.PermissionEnum.ANALYTICS_VIEW;
import static com.pharmasense.identity.enums.PermissionEnum.CATALOG_SCAN;
import static com.pharmasense.identity.enums.PermissionEnum.INVENTORY_DELETE;
import static com.pharmasense.identity.enums.PermissionEnum.INVENTORY_READ;
import static com.pharmasense.identity.enums.PermissionEnum.INVENTORY_WRITE;
import static com.pharmasense.identity.enums.PermissionEnum.PHARMACY_SETTINGS_MANAGE;
import static com.pharmasense.identity.enums.PermissionEnum.PRESCRIPTION_READ;
import static com.pharmasense.identity.enums.PermissionEnum.PRESCRIPTION_WRITE;
import static com.pharmasense.identity.enums.PermissionEnum.STAFF_MANAGE;

/**
 * Every role a {@code UserAccountEntity} can hold. {@code SUPER_ADMIN} is the
 * one role not scoped to a pharmacy - it operates through the admin module
 * instead of the tenant-scoped feature APIs, so its permission set here is
 * intentionally empty and is short-circuited separately in
 * {@link com.pharmasense.identity.security.RbacEvaluator}.
 */
public enum UserRoleEnum {

    OWNER(Set.of(
            INVENTORY_READ, INVENTORY_WRITE, INVENTORY_DELETE,
            PRESCRIPTION_READ, PRESCRIPTION_WRITE,
            CATALOG_SCAN, STAFF_MANAGE, PHARMACY_SETTINGS_MANAGE,
            ANALYTICS_VIEW, AGENT_USE)),

    PHARMACIST(Set.of(
            INVENTORY_READ, INVENTORY_WRITE,
            PRESCRIPTION_READ, PRESCRIPTION_WRITE,
            CATALOG_SCAN, ANALYTICS_VIEW, AGENT_USE)),

    STAFF(Set.of(
            INVENTORY_READ, INVENTORY_WRITE,
            PRESCRIPTION_READ,
            CATALOG_SCAN, AGENT_USE)),

    SUPER_ADMIN(Set.of());

    private final Set<PermissionEnum> permissions;

    UserRoleEnum(Set<PermissionEnum> permissions) {
        this.permissions = permissions;
    }

    public Set<PermissionEnum> getPermissions() {
        return permissions;
    }
}
