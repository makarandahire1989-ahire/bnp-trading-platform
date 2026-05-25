package com.trading.platform.dto.response;

import com.trading.platform.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Product details including current inventory state")
public record ProductResponse(

        @Schema(description = "Unique product identifier")
        UUID id,

        @Schema(description = "Product name")
        String name,

        @Schema(description = "Original total inventory")
        int totalQuantity,

        @Schema(description = "Units currently available for new reservations")
        int availableQty,

        @Schema(description = "Units currently reserved (pending payment)")
        int reservedQty,

        @Schema(description = "Units permanently sold (confirmed orders)")
        int soldQty,

        @Schema(description = "When the product was registered")
        Instant createdAt
) {
    public static ProductResponse from(Product p) {
        int soldQty = p.getTotalQuantity() - p.getAvailableQty() - p.getReservedQty();
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getTotalQuantity(),
                p.getAvailableQty(),
                p.getReservedQty(),
                soldQty,
                p.getCreatedAt()
        );
    }
}
