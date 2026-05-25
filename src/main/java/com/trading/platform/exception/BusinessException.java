package com.trading.platform.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain-level business rule violations.
 * Maps to a specific HTTP status code so the global handler
 * can respond without inspecting exception types individually.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;

    public BusinessException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
