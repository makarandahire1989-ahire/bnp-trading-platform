package com.trading.platform.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId, HttpStatus.NOT_FOUND);
    }
}
