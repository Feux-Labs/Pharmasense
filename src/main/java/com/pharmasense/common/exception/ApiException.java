package com.pharmasense.common.exception;

import lombok.Getter;

/**
 * Base for every deliberately-thrown business exception. Caught centrally by
 * {@link GlobalExceptionHandler} and translated into an {@code ApiResponse}
 * using {@link #errorCode}'s mapped HTTP status.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
