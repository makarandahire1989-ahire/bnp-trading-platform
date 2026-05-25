package com.trading.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int availableQty;

    @Column(nullable = false)
    private int reservedQty;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    public Product() {}

    public Product(String name, int totalQuantity) {
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.availableQty = totalQuantity;
        this.reservedQty = 0;
    }

    /**
     * Attempts to reserve the requested quantity.
     * @throws IllegalStateException if insufficient stock.
     */
    public void reserve(int quantity) {
        if (availableQty < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock: requested=%d, available=%d".formatted(quantity, availableQty));
        }
        availableQty -= quantity;
        reservedQty += quantity;
        updatedAt = Instant.now();
    }

    /**
     * Releases previously reserved quantity back to available.
     * Used by expiry scheduler and rejection path.
     */
    public void release(int quantity) {
        reservedQty -= quantity;
        availableQty += quantity;
        updatedAt = Instant.now();
    }

    /**
     * Confirms a reservation — removes from reserved without returning to available.
     * Called when payment succeeds.
     */
    public void confirm(int quantity) {
        reservedQty -= quantity;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getAvailableQty() { return availableQty; }
    public int getReservedQty() { return reservedQty; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
