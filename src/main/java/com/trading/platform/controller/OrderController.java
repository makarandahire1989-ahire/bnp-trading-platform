package com.trading.platform.controller;

import com.trading.platform.dto.request.PlaceOrderRequest;
import com.trading.platform.dto.response.ApiResponse;
import com.trading.platform.dto.response.OrderResponse;
import com.trading.platform.filter.CorrelationIdFilter;
import com.trading.platform.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order lifecycle: place → pay → confirm")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(
        summary = "Place an order",
        description = """
            Reserves the requested quantity for the client.
            
            - Returns **201 CREATED** with status `PENDING_PAYMENT` when stock is reserved.
            - Returns **409 CONFLICT** when there is insufficient product inventory; an order record with
              status `REJECTED` is still saved to database for audit purposes.
            
            The reservation is valid for the configured window (default **15 minutes**,  Current configuration **15 minutes**).
            After that the inventory is automatically released and the order transitions to `EXPIRED`.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
            description = "Order created and stock reserved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "Insufficient stock inventory — order rejected"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Invalid request payload")
    })
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            HttpServletRequest httpRequest) {

        UUID correlationId = correlationId(httpRequest);
        OrderResponse order = orderService.placeOrder(request, correlationId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(correlationId, "Order created, stock reserved", order));
    }

    @PostMapping("/{orderId}/pay")
    @Operation(
        summary = "Confirm payment",
        description = """
            Accepts payment for a `PENDING_PAYMENT` order and transitions it to `CONFIRMED`.
            
            **Idempotency**: re-invoking on an already-`CONFIRMED` order returns **422**.
            
            **Expiry check**: if the reservation window has elapsed by the time payment arrives
            the order is immediately transitioned to `EXPIRED` and inventory is released.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Payment confirmed — order is CONFIRMED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
            description = "Order is not in PENDING_PAYMENT state (already CONFIRMED, REJECTED, or EXPIRED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "Order not found")
    })
    public ResponseEntity<ApiResponse<OrderResponse>> confirmPayment(
            @PathVariable UUID orderId,
            HttpServletRequest httpRequest) {

        UUID correlationId = correlationId(httpRequest);
        OrderResponse order = orderService.confirmPayment(orderId, correlationId);
        return ResponseEntity.ok(ApiResponse.of(correlationId, "Payment confirmed", order));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order status",
               description = "Returns the current state and full lifecycle timestamps of an order.")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable UUID orderId,
            HttpServletRequest httpRequest) {

        UUID correlationId = correlationId(httpRequest);
        OrderResponse order = orderService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.of(correlationId, "Order retrieved", order));
    }

    private UUID correlationId(HttpServletRequest req) {
        Object attr = req.getAttribute(CorrelationIdFilter.ATTR_CORRELATION_ID);
        return attr instanceof UUID id ? id : UUID.randomUUID();
    }
}
