package com.pharmasense.identity.enums;

/**
 * Fine-grained capabilities checked by {@code @PreAuthorize} across the API.
 * Roles map to a set of these (see {@link UserRoleEnum#getPermissions()})
 * instead of endpoints checking role names directly, so tightening or
 * loosening what a role can do never means touching controller code.
 */
public enum PermissionEnum {
    INVENTORY_READ,
    INVENTORY_WRITE,
    INVENTORY_DELETE,
    PRESCRIPTION_READ,
    PRESCRIPTION_WRITE,
    CATALOG_SCAN,
    STAFF_MANAGE,
    PHARMACY_SETTINGS_MANAGE,
    ANALYTICS_VIEW,
    AGENT_USE
}
