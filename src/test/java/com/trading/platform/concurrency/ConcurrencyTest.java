package com.trading.platform.concurrency;

import com.trading.platform.dto.request.CreateProductRequest;
import com.trading.platform.dto.request.PlaceOrderRequest;
import com.trading.platform.dto.response.OrderResponse;
import com.trading.platform.dto.response.ProductResponse;
import com.trading.platform.exception.InsufficientInventoryException;
import com.trading.platform.service.OrderService;
import com.trading.platform.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stress tests for concurrent order placement.
 *
 * <p>These tests simulate many clients racing to buy from limited inventory
 * and verify that:
 * <ul>
 *   <li>Inventory never goes negative.</li>
 *   <li>The total confirmed + reserved quantities never exceed the product total.</li>
 *   <li>No orders are silently lost — every thread gets either a success or a clear rejection.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired ProductService productService;
    @Autowired OrderService   orderService;

    @Test
    @DisplayName("50 concurrent orders for 100 units of stock — inventory never goes negative")
    void concurrentOrders_inventoryNeverNegative() throws InterruptedException {
        int totalStock      = 100;
        int threads         = 50;
        int quantityPerOrder = 3;

        UUID productId = productService.createProduct(
                new CreateProductRequest("Concurrent Stock", totalStock)
        ).id();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready     = new CountDownLatch(threads);
        CountDownLatch start     = new CountDownLatch(1);
        AtomicInteger  success   = new AtomicInteger();
        AtomicInteger  rejected  = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    orderService.placeOrder(
                            new PlaceOrderRequest(productId, quantityPerOrder, null),
                            UUID.randomUUID()
                    );
                    success.incrementAndGet();
                } catch (InsufficientInventoryException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }

        ready.await();
        start.countDown();

        for (Future<Void> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); }
            catch (ExecutionException | TimeoutException ignored) {}
        }
        executor.shutdown();

        ProductResponse product = productService.getProduct(productId);

        assertThat(product.availableQty())
                .as("Available quantity must never go negative")
                .isGreaterThanOrEqualTo(0);

        assertThat(product.reservedQty())
                .as("Reserved quantity must never go negative")
                .isGreaterThanOrEqualTo(0);

        assertThat(product.availableQty() + product.reservedQty() + product.soldQty())
                .as("available + reserved + sold must equal total")
                .isEqualTo(totalStock);

        assertThat(success.get() + rejected.get())
                .as("Every thread must get a definitive answer")
                .isEqualTo(threads);

        System.out.printf("Concurrency result: success=%d rejected=%d available=%d reserved=%d%n",
                success.get(), rejected.get(), product.availableQty(), product.reservedQty());
    }

    @Test
    @DisplayName("Race to buy last unit — exactly one winner")
    void raceForLastUnit_exactlyOneWinner() throws InterruptedException {
        int threads = 20;
        UUID productId = productService.createProduct(
                new CreateProductRequest("Last-Unit Race", 1)
        ).id();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch  ready    = new CountDownLatch(threads);
        CountDownLatch  start    = new CountDownLatch(1);
        AtomicInteger   wins     = new AtomicInteger();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    orderService.placeOrder(
                            new PlaceOrderRequest(productId, 1, null),
                            UUID.randomUUID()
                    );
                    wins.incrementAndGet();
                } catch (InsufficientInventoryException ignored) {}
                return null;
            }));
        }

        ready.await();
        start.countDown();
        for (Future<Void> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        assertThat(wins.get())
                .as("Exactly one thread should win the last unit")
                .isEqualTo(1);

        ProductResponse product = productService.getProduct(productId);
        assertThat(product.availableQty()).isEqualTo(0);
        assertThat(product.reservedQty()).isEqualTo(1);
    }
}
