package com.trading.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request payload to place an order for a product")
public record PlaceOrderRequest(

        @NotNull(message = "Product ID is required")
        @Schema(description = "ID of the product to order", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID productId,

        @NotNull
        @Min(value = 1, message = "Quantity must be at least 1")
        @Schema(description = "Number of units to reserve", example = "10")
        Integer quantity,

        @Schema(description = "Optional client-side reference for idempotency or tracking",
                example = "CLIENT-REF-20260101-001")
        String clientRef
) {}
