package com.pharmasense.inventory.enums;

/** Computed on read from expiry date vs. today and the pharmacy's warning window - never persisted. */
public enum ExpiryStatusEnum {
    NOT_APPLICABLE,
    FRESH,
    EXPIRING_SOON,
    EXPIRED
}
