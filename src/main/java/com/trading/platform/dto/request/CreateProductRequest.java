package com.trading.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload to register a new product")
public record CreateProductRequest(

        @NotBlank(message = "Product name must not be blank")
        @Schema(description = "Human-readable name of the product", example = "Tata Corp Stock Unit")
        String name,

        @NotNull
        @Min(value = 1, message = "Total quantity must be at least 1")
        @Schema(description = "Total inventory units available for sale", example = "1000")
        Integer totalQuantity
) {}
