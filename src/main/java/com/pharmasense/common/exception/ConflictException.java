package com.pharmasense.common.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }
}
