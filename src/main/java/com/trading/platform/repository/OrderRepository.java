package com.trading.platform.repository;

import com.trading.platform.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Finds all PENDING_PAYMENT orders whose reservation window has elapsed.
     * Called periodically by the expiry scheduler.
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING_PAYMENT' AND o.expiresAt < :now")
    List<Order> findExpiredPendingOrders(@Param("now") Instant now);

    /** Count orders by status — used for metrics/observability. */
    long countByStatus(Order.Status status);
}
