package com.trading.platform.scheduler;

import com.trading.platform.dto.request.CreateProductRequest;
import com.trading.platform.dto.request.PlaceOrderRequest;
import com.trading.platform.dto.response.OrderResponse;
import com.trading.platform.entity.Order;
import com.trading.platform.repository.OrderRepository;
import com.trading.platform.service.OrderService;
import com.trading.platform.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for reservation expiry logic.
 *
 * <p>We manipulate {@code expiresAt} directly via the repository rather than
 * waiting real wall-clock minutes, making tests fast and deterministic.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationExpiryTest {

    @Autowired ProductService            productService;
    @Autowired OrderService              orderService;
    @Autowired OrderRepository           orderRepository;
    @Autowired ReservationExpiryScheduler expiryScheduler;

    @Test
    @DisplayName("expired order transitions to EXPIRED and releases inventory")
    @Transactional
    void expiredOrder_releasesInventory() {
        UUID productId = productService.createProduct(
                new CreateProductRequest("Expiry Test Stock", 50)).id();

        OrderResponse placed = orderService.placeOrder(
                new PlaceOrderRequest(productId, 10, null), UUID.randomUUID());

        forceExpiry(placed.id());

        expiryScheduler.expireStaleReservations();

        OrderResponse fetched = orderService.getOrder(placed.id());
        assertThat(fetched.status()).isEqualTo("EXPIRED");

        var product = productService.getProduct(productId);
        assertThat(product.availableQty()).isEqualTo(50);
        assertThat(product.reservedQty()).isEqualTo(0);
    }

    @Test
    @DisplayName("confirmed order is not touched by the expiry scheduler")
    @Transactional
    void confirmedOrder_notAffectedByScheduler() {
        UUID productId = productService.createProduct(
                new CreateProductRequest("Confirmed Stock", 50)).id();

        UUID orderId = orderService.placeOrder(
                new PlaceOrderRequest(productId, 10, null), UUID.randomUUID()).id();

        orderService.confirmPayment(orderId, UUID.randomUUID());

        forceExpiry(orderId);
        expiryScheduler.expireStaleReservations();

        OrderResponse fetched = orderService.getOrder(orderId);
        assertThat(fetched.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("scheduler only expires orders past their expiry timestamp")
    @Transactional
    void scheduler_doesNotExpireActivePendingOrders() {
        UUID productId = productService.createProduct(
                new CreateProductRequest("Active Stock", 50)).id();

        orderService.placeOrder(new PlaceOrderRequest(productId, 5, null), UUID.randomUUID());

        expiryScheduler.expireStaleReservations();

        List<Order> pending = orderRepository.findExpiredPendingOrders(Instant.now());
        assertThat(pending).isEmpty();

        var product = productService.getProduct(productId);
        assertThat(product.availableQty()).isEqualTo(45);
        assertThat(product.reservedQty()).isEqualTo(5);
    }

    private void forceExpiry(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        orderRepository.save(backdateExpiry(order));
    }

    private Order backdateExpiry(Order order) {
        try {
            var field = Order.class.getDeclaredField("expiresAt");
            field.setAccessible(true);
            field.set(order, Instant.now().minusSeconds(3600));
            return order;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
