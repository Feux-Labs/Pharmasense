package com.pharmasense.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return, paired with the HTTP status it maps to.
 * Frontend and the agentic AI tool layer branch on {@code errorCode}, never
 * on the human-readable message, so keep these stable once shipped.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    OTP_INVALID_OR_EXPIRED(HttpStatus.UNAUTHORIZED),
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TENANT_SUBSCRIPTION_INACTIVE(HttpStatus.PAYMENT_REQUIRED),
    SUBSCRIPTION_LIMIT_REACHED(HttpStatus.PAYMENT_REQUIRED),
    AGENT_TOOL_EXECUTION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    AGENT_PERMISSION_DENIED(HttpStatus.FORBIDDEN),
    SYNC_CONFLICT(HttpStatus.CONFLICT),
    UPSTREAM_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
