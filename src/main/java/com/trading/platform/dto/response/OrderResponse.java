package com.trading.platform.dto.response;

import com.trading.platform.entity.Order;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Order details including lifecycle state and timestamps")
public record OrderResponse(

        @Schema(description = "Unique order identifier")
        UUID id,

        @Schema(description = "Product this order is for")
        UUID productId,

        @Schema(description = "Quantity of units ordered")
        int quantity,

        @Schema(description = "Current order status",
                allowableValues = {"PENDING_PAYMENT", "CONFIRMED", "REJECTED", "EXPIRED"})
        String status,

        @Schema(description = "Correlation ID for request tracing")
        UUID correlationId,

        @Schema(description = "Client-supplied reference, if provided")
        String clientRef,

        @Schema(description = "When the order was placed")
        Instant createdAt,

        @Schema(description = "When the reservation expires (null for non-pending orders)")
        Instant expiresAt,

        @Schema(description = "When the payment was confirmed (null if not yet paid)")
        Instant paidAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getProductId(),
                o.getQuantity(),
                o.getStatus().name(),
                o.getCorrelationId(),
                o.getClientRef(),
                o.getCreatedAt(),
                o.getExpiresAt(),
                o.getPaidAt()
        );
    }
}
