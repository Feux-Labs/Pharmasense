package com.pharmasense.common.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceName + " not found: " + identifier);
    }
}
