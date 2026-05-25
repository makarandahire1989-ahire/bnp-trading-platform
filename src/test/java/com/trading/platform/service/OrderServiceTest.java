package com.trading.platform.service;

import com.trading.platform.dto.request.PlaceOrderRequest;
import com.trading.platform.dto.response.OrderResponse;
import com.trading.platform.dto.response.ProductResponse;
import com.trading.platform.exception.InsufficientInventoryException;
import com.trading.platform.exception.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest {

    @Autowired ProductService productService;
    @Autowired OrderService   orderService;

    private UUID productId;
    private static final int TOTAL_QTY = 100;

    @BeforeEach
    void setUp() {
        productId = productService.createProduct(
                new com.trading.platform.dto.request.CreateProductRequest("Test Stock", TOTAL_QTY)
        ).id();
    }

    @Nested
    @DisplayName("Place Order")
    class PlaceOrderTests {

        @Test
        @DisplayName("should reserve inventory when stock is available")
        void placeOrder_success() {
            OrderResponse order = placeOrder(10);

            assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(order.quantity()).isEqualTo(10);
            assertThat(order.expiresAt()).isNotNull();

            ProductResponse product = productService.getProduct(productId);
            assertThat(product.availableQty()).isEqualTo(TOTAL_QTY - 10);
            assertThat(product.reservedQty()).isEqualTo(10);
        }

        @Test
        @DisplayName("should reject order when stock is insufficient")
        void placeOrder_insufficientStock() {
            assertThatThrownBy(() -> placeOrder(TOTAL_QTY + 1))
                    .isInstanceOf(InsufficientInventoryException.class);
        }

        @Test
        @DisplayName("should not change available quantity when order is rejected")
        void placeOrder_rejectedDoesNotChangeInventory() {
            assertThatThrownBy(() -> placeOrder(TOTAL_QTY + 1))
                    .isInstanceOf(InsufficientInventoryException.class);

            ProductResponse product = productService.getProduct(productId);
            assertThat(product.availableQty()).isEqualTo(TOTAL_QTY);
            assertThat(product.reservedQty()).isEqualTo(0);
        }

        @Test
        @DisplayName("multiple orders should drain available stock correctly")
        void placeOrder_multipleOrders_drainStock() {
            placeOrder(30);
            placeOrder(20);

            ProductResponse product = productService.getProduct(productId);
            assertThat(product.availableQty()).isEqualTo(50);
            assertThat(product.reservedQty()).isEqualTo(50);
        }

        @Test
        @DisplayName("exact quantity order succeeds and leaves zero available")
        void placeOrder_exactQuantity() {
            OrderResponse order = placeOrder(TOTAL_QTY);
            assertThat(order.status()).isEqualTo("PENDING_PAYMENT");

            ProductResponse product = productService.getProduct(productId);
            assertThat(product.availableQty()).isEqualTo(0);
            assertThat(product.reservedQty()).isEqualTo(TOTAL_QTY);
        }
    }

    @Nested
    @DisplayName("Confirm Payment")
    class ConfirmPaymentTests {

        @Test
        @DisplayName("should confirm a pending order")
        void confirmPayment_success() {
            UUID orderId = placeOrder(10).id();
            OrderResponse confirmed = orderService.confirmPayment(orderId, UUID.randomUUID());

            assertThat(confirmed.status()).isEqualTo("CONFIRMED");
            assertThat(confirmed.paidAt()).isNotNull();

            ProductResponse product = productService.getProduct(productId);
            assertThat(product.reservedQty()).isEqualTo(0);
            assertThat(product.availableQty()).isEqualTo(TOTAL_QTY - 10);
            assertThat(product.soldQty()).isEqualTo(10);
        }

        @Test
        @DisplayName("should reject second payment on already confirmed order (idempotency)")
        void confirmPayment_alreadyConfirmed_rejected() {
            UUID orderId = placeOrder(10).id();
            orderService.confirmPayment(orderId, UUID.randomUUID());

            assertThatThrownBy(() -> orderService.confirmPayment(orderId, UUID.randomUUID()))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("sold quantity must never exceed total quantity")
        void confirmPayment_soldNeverExceedsTotal() {
            placeOrder(50);
            UUID orderId2 = placeOrder(50).id();

            orderService.confirmPayment(orderId2, UUID.randomUUID());

            ProductResponse product = productService.getProduct(productId);
            assertThat(product.soldQty()).isLessThanOrEqualTo(TOTAL_QTY);
        }
    }

    @Nested
    @DisplayName("Order Query")
    class OrderQueryTests {

        @Test
        @DisplayName("should return correct order details")
        void getOrder_returnsDetails() {
            UUID orderId = placeOrder(5).id();
            OrderResponse fetched = orderService.getOrder(orderId);

            assertThat(fetched.id()).isEqualTo(orderId);
            assertThat(fetched.quantity()).isEqualTo(5);
            assertThat(fetched.productId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("should return correlation ID on the order")
        void getOrder_hasCorrelationId() {
            UUID correlationId = UUID.randomUUID();
            PlaceOrderRequest req = new PlaceOrderRequest(productId, 5, "my-ref");
            UUID orderId = orderService.placeOrder(req, correlationId).id();

            OrderResponse fetched = orderService.getOrder(orderId);
            assertThat(fetched.correlationId()).isEqualTo(correlationId);
            assertThat(fetched.clientRef()).isEqualTo("my-ref");
        }
    }

    private OrderResponse placeOrder(int qty) {
        return orderService.placeOrder(
                new PlaceOrderRequest(productId, qty, null),
                UUID.randomUUID()
        );
    }
}
