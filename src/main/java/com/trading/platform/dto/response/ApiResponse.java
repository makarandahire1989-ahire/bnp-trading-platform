package com.trading.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Uniform JSON envelope returned by every endpoint.
 *
 * <p>Every response carries:
 * <ul>
 *   <li>{@code correlationId} – trace ID injected by {@code CorrelationIdFilter}, propagated via MDC.</li>
 *   <li>{@code timestamp} – server-side ISO-8601 instant; allows support to reconstruct the processing path.</li>
 *   <li>{@code message} – human-readable status summary.</li>
 *   <li>{@code data} – typed payload; {@code null} on error responses.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public record ApiResponse<T>(

        @Schema(description = "Unique identifier for this request, present in all logs", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID correlationId,

        @Schema(description = "Server timestamp when this response was generated")
        Instant timestamp,

        @Schema(description = "Human-readable summary of the outcome", example = "Order created, stock reserved")
        String message,

        @Schema(description = "Response payload; absent on error responses")
        T data
) {
    public static <T> ApiResponse<T> of(UUID correlationId, String message, T data) {
        return new ApiResponse<>(correlationId, Instant.now(), message, data);
    }

    public static <T> ApiResponse<T> error(UUID correlationId, String message) {
        return new ApiResponse<>(correlationId, Instant.now(), message, null);
    }
}
