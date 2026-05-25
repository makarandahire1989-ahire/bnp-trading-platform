package com.trading.platform.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a client attempts to reserve more stock than is currently available.
 * The order will be created in REJECTED state and this exception drives the 409 response.
 */
public class InsufficientInventoryException extends BusinessException {

    public InsufficientInventoryException(int requested, int available) {
        super("Insufficient inventory: requested=%d, available=%d".formatted(requested, available),
              HttpStatus.CONFLICT);
    }
}
