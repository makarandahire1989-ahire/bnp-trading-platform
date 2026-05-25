package com.trading.platform.controller;

import com.trading.platform.dto.request.CreateProductRequest;
import com.trading.platform.dto.response.ApiResponse;
import com.trading.platform.dto.response.ProductResponse;
import com.trading.platform.filter.CorrelationIdFilter;
import com.trading.platform.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product registration and inventory queries")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Register a product",
               description = "Creates a new product with a fixed inventory quantity.")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest httpRequest) {

        UUID correlationId = correlationId(httpRequest);
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(correlationId, "Product created successfully", product));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get product inventory",
               description = "Returns a product's current available, reserved, and sold quantities.")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable UUID productId,
            HttpServletRequest httpRequest) {

        UUID correlationId = correlationId(httpRequest);
        ProductResponse product = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.of(correlationId, "Product retrieved", product));
    }

    private UUID correlationId(HttpServletRequest req) {
        Object attr = req.getAttribute(CorrelationIdFilter.ATTR_CORRELATION_ID);
        return attr instanceof UUID id ? id : UUID.randomUUID();
    }
}
