package com.pharmasense.identity.enums;

/**
 * Scopes an OTP to what it's allowed to authorize, so a code requested for
 * one flow (e.g. passwordless login) can't be replayed against another
 * (e.g. resetting a password) even though both just prove email ownership.
 */
public enum OtpPurpose {
    LOGIN,
    PASSWORD_RESET
}
