package com.trading.platform.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException(UUID productId) {
        super("Product not found: " + productId, HttpStatus.NOT_FOUND);
    }
}
