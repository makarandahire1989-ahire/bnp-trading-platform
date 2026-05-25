package com.trading.platform.service;

import com.trading.platform.config.AppConfig;
import com.trading.platform.dto.request.PlaceOrderRequest;
import com.trading.platform.dto.response.OrderResponse;
import com.trading.platform.entity.Order;
import com.trading.platform.entity.Product;
import com.trading.platform.exception.*;
import com.trading.platform.repository.OrderRepository;
import com.trading.platform.repository.ProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Core order lifecycle service.
 *
 * <h2>Concurrency strategy</h2>
 * <p>Order placement uses {@link Isolation#READ_COMMITTED} combined with a
 * <em>pessimistic write lock</em> ({@code SELECT FOR UPDATE}) on the product row.
 * This means:
 * <ul>
 *   <li>Concurrent {@code placeOrder} calls for the same product queue at the DB lock,
 *       so inventory arithmetic is always single-threaded per product.</li>
 *   <li>The transaction reads the freshest committed quantity, updates it, and releases the
 *       lock atomically — eliminating lost-update and phantom-read races.</li>
 *   <li>The {@link Product#reserve(int)} method has a guard that throws if available drops
 *       below zero; combined with the DB {@code CHECK} constraint this is doubly safe.</li>
 * </ul>
 *
 * <h2>Reservation expiry</h2>
 * <p>Each {@code PENDING_PAYMENT} order carries an {@code expiresAt} timestamp.
 * The {@link com.trading.platform.scheduler.ReservationExpiryScheduler} polls for
 * overdue orders and calls {@link #expireOrder(UUID)} for each one, releasing inventory.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository    orderRepository;
    private final ProductRepository  productRepository;
    private final AppConfig          appConfig;
    private final MeterRegistry      meterRegistry;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        AppConfig appConfig,
                        MeterRegistry meterRegistry) {
        this.orderRepository   = orderRepository;
        this.productRepository = productRepository;
        this.appConfig         = appConfig;
        this.meterRegistry     = meterRegistry;
    }

    /**
     * Confirms payment for a pending order.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>Only {@code PENDING_PAYMENT} orders may be confirmed (idempotency guard).</li>
     *   <li>The reservation must not have expired by the time payment arrives.</li>
     *   <li>Inventory is permanently deducted from {@code reservedQty} (no longer available or reserved).</li>
     * </ul>
     *
     * @param orderId      the order to confirm
     * @param correlationId request trace ID
     * @return updated order view
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse confirmPayment(UUID orderId, UUID correlationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.isPendingPayment()) {
            log.warn("Payment rejected — order not in PENDING_PAYMENT: orderId={} status={}",
                    orderId, order.getStatus());
            throw new InvalidOrderStateException(orderId, order.getStatus(), "pay");
        }

        Instant now = Instant.now();
        UUID productId = order.getProductId();

        if (order.isExpiredByTime(now)) {
            Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            product.release(order.getQuantity());
            productRepository.save(product);
            order.expire(now);
            orderRepository.save(order);

            log.warn("Payment rejected — reservation expired: orderId={} expiredAt={}", orderId, order.getExpiresAt());
            meterRegistry.counter("orders.expired").increment();
            throw new InvalidOrderStateException(orderId, Order.Status.EXPIRED, "pay");
        }

        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.confirm(order.getQuantity());
        productRepository.save(product);

        order.confirm(now);
        Order confirmed = orderRepository.save(order);

        log.info("Order CONFIRMED orderId={} productId={} qty={} paidAt={}",
                orderId, confirmed.getProductId(), confirmed.getQuantity(), confirmed.getPaidAt());
        meterRegistry.counter("orders.confirmed").increment();

        return OrderResponse.from(confirmed);
    }

    /**
     * Returns the current state of an order.
     *
     * @param orderId order to query
     * @return order view
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.from(order);
    }

    /**
     * Places an order for a product.
     *
     * <p>Steps (all within one transaction):
     * <ol>
     *   <li>Acquire a pessimistic write lock on the product row.</li>
     *   <li>Attempt to reserve the requested quantity.</li>
     *   <li>If stock is sufficient: create {@code PENDING_PAYMENT} order and save both entities.</li>
     *   <li>If stock is insufficient: create {@code REJECTED} order (no inventory change) and throw
     *       {@link InsufficientInventoryException} so the caller gets a 409.</li>
     * </ol>
     *
     * @param request    placement payload
     * @param correlationId request trace ID from the HTTP filter
     * @return order view
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse placeOrder(PlaceOrderRequest request, UUID correlationId) {
        Product product = productRepository.findByIdWithLock(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        if (product.getAvailableQty() < request.quantity()) {
            Order rejectedOrder = new Order(
                    product.getId(), request.quantity(), correlationId,
                    request.clientRef(), null);
            rejectedOrder.reject(Instant.now());
            orderRepository.save(rejectedOrder);

            log.warn("Order REJECTED productId={} requested={} available={}",
                    product.getId(), request.quantity(), product.getAvailableQty());
            meterRegistry.counter("orders.rejected").increment();

            throw new InsufficientInventoryException(request.quantity(), product.getAvailableQty());
        }

        Instant expiresAt = Instant.now().plus(appConfig.getReservationWindowMinutes(), ChronoUnit.MINUTES);
        Order order = new Order(product.getId(), request.quantity(), correlationId,
                request.clientRef(), expiresAt);

        product.reserve(request.quantity());

        productRepository.save(product);
        order = orderRepository.save(order);

        log.info("Order PENDING_PAYMENT orderId={} productId={} qty={} expiresAt={}",
                order.getId(), product.getId(), request.quantity(), expiresAt);
        meterRegistry.counter("orders.pending").increment();

        return OrderResponse.from(order);
    }

    /**
     * Called by the expiry scheduler to transition an overdue pending order to {@code EXPIRED}
     * and return its reserved inventory to the pool.
     *
     * <p>Each expiry runs in its own transaction so a failure on one order does not block others.
     *
     * @param orderId the order to expire
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expireOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.isPendingPayment()) {
            return;
        }

        Product product = productRepository.findByIdWithLock(order.getProductId())
                .orElse(null);
        if (product != null) {
            product.release(order.getQuantity());
            productRepository.save(product);
        }

        order.expire(Instant.now());
        orderRepository.save(order);

        log.info("Order EXPIRED orderId={} productId={} qty={} releasedBack={}",
                orderId, order.getProductId(), order.getQuantity(), order.getQuantity());
        meterRegistry.counter("orders.expired").increment();
    }
}
