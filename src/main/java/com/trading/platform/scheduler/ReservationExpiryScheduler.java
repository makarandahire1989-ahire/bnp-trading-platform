package com.trading.platform.scheduler;

import com.trading.platform.entity.Order;
import com.trading.platform.repository.OrderRepository;
import com.trading.platform.service.OrderService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodically scans for {@code PENDING_PAYMENT} orders whose reservation window has
 * elapsed and transitions them to {@code EXPIRED}, releasing their reserved inventory.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>The scan interval is configurable via {@code app.reservation.cleanup-interval-ms}
 *       (default 60 s). Shorter intervals reduce the window of "zombie" reservations at the
 *       cost of more DB queries.</li>
 *   <li>Each order is expired in its own transaction (delegated to {@link OrderService#expireOrder})
 *       so a transient failure on one row does not prevent others from being processed.</li>
 *   <li>Prometheus gauges report pending/confirmed/expired counts for alerting.</li>
 * </ul>
 */
@Component
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final OrderService    orderService;
    private final OrderRepository orderRepository;

    public ReservationExpiryScheduler(OrderService orderService,
                                      OrderRepository orderRepository,
                                      MeterRegistry meterRegistry) {
        this.orderService    = orderService;
        this.orderRepository = orderRepository;

        Gauge.builder("orders.gauge.pending",  orderRepository, r -> r.countByStatus(Order.Status.PENDING_PAYMENT))
                .description("Number of orders currently in PENDING_PAYMENT state")
                .register(meterRegistry);

        Gauge.builder("orders.gauge.confirmed", orderRepository, r -> r.countByStatus(Order.Status.CONFIRMED))
                .description("Total confirmed orders")
                .register(meterRegistry);

        Gauge.builder("orders.gauge.expired",   orderRepository, r -> r.countByStatus(Order.Status.EXPIRED))
                .description("Total expired orders")
                .register(meterRegistry);
    }

    /**
     * Fixed-delay scan — starts {@code cleanup-interval-ms} after the previous run finishes.
     * Using fixed delay (not fixed rate) avoids pile-up if a scan takes longer than the interval.
     */
    @Scheduled(fixedDelayString = "${app.reservation.cleanup-interval-ms:60000}")
    public void expireStaleReservations() {
        Instant now = Instant.now();
        List<Order> expired = orderRepository.findExpiredPendingOrders(now);

        if (expired.isEmpty()) {
            log.debug("Expiry scan complete — no stale reservations found");
            return;
        }

        log.info("Expiry scan: found {} stale reservation(s) to expire", expired.size());

        for (Order order : expired) {
            try {
                orderService.expireOrder(order.getId());
            } catch (Exception ex) {
                log.error("Failed to expire order {}: {}", order.getId(), ex.getMessage(), ex);
            }
        }

        log.info("Expiry scan complete — expired {} order(s)", expired.size());
    }
}
