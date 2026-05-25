package com.trading.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status {
        PENDING_PAYMENT,
        CONFIRMED,
        REJECTED,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant expiresAt;

    private Instant paidAt;

    @Column(nullable = false)
    private UUID correlationId;

    private String clientRef;

    public Order() {}

    public Order(UUID productId, int quantity, UUID correlationId, String clientRef, Instant expiresAt) {
        this.productId = productId;
        this.quantity = quantity;
        this.correlationId = correlationId;
        this.clientRef = clientRef;
        this.expiresAt = expiresAt;
        this.status = Status.PENDING_PAYMENT;
    }

    public boolean isPendingPayment() {
        return status == Status.PENDING_PAYMENT;
    }

    public boolean isExpiredByTime(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public void confirm(Instant now) {
        this.status = Status.CONFIRMED;
        this.paidAt = now;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        this.status = Status.EXPIRED;
        this.updatedAt = now;
    }

    public void reject(Instant now) {
        this.status = Status.REJECTED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public UUID getCorrelationId() { return correlationId; }
    public String getClientRef() { return clientRef; }
}
