package com.trading.platform.repository;

import com.trading.platform.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT FOR UPDATE) lock on the product row.
     *
     * This is the concurrency guardrail: only one transaction at a time can
     * hold the lock, so concurrent order-placement requests are serialised
     * at the DB level and cannot race against each other for inventory.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") UUID id);
}
