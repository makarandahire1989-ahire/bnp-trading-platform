package com.trading.platform.exception;

import com.trading.platform.entity.Order;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvalidOrderStateException extends BusinessException {

    public InvalidOrderStateException(UUID orderId, Order.Status actual, String action) {
        super("Cannot %s order %s: current status is %s".formatted(action, orderId, actual),
              HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
