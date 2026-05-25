# Equity Trading Platform

A high-speed, stock-reservation service implementing the **reserve → pay → confirm** workflow with full concurrency safety.

---

## Quick Start

```bash
cd artifacts/bnp-trading-platform
mvn spring-boot:run
```

The service starts on **port 8080** (override with `PORT` env var).

| UI | URL |
|----|-----|
| Swagger UI (Demo) | http://localhost:8080/swagger-ui.html |
| H2 Console | http://localhost:8080/h2-console |
| Health check | http://localhost:8080/actuator/health |
| Metrics (Prometheus) | http://localhost:8080/actuator/prometheus |

---

## API Overview

| Verb | Path | Purpose |
|------|------|---------|
| `POST` | `/api/products` | Register a product with fixed inventory |
| `GET` | `/api/products/{id}` | View available, reserved, and sold quantities |
| `POST` | `/api/orders` | Place an order; reserves stock for 15 min |
| `POST` | `/api/orders/{id}/pay` | Confirm payment for a pending order |
| `GET` | `/api/orders/{id}` | Query current order status |

### Standard Response Envelope

Every response follows this shape:

```json
{
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-05-23T14:33:12.456Z",
  "message": "Order created, stock reserved",
  "data": { ... }
}
```

---

## Full Demo Walkthrough (curl)

```bash
# 1. Create a product with 100 units
curl -s -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"ACME Corp Stock","totalQuantity":100}' | jq .

# 2. Place an order (copy productId from step 1)
curl -s -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"productId":"<productId>","quantity":10}' | jq .

# 3. Confirm payment (copy orderId from step 2)
curl -s -X POST http://localhost:8080/api/orders/<orderId>/pay | jq .

# 4. Check order status
curl -s http://localhost:8080/api/orders/<orderId> | jq .

# 5. Check product inventory
curl -s http://localhost:8080/api/products/<productId> | jq .
```

---

## Running Tests

```bash
# All tests (unit + integration + concurrency)
cd artifacts/trading-platform && mvn test

# Specific test class
mvn test -Dtest=ConcurrencyTest
mvn test -Dtest=OrderServiceTest
mvn test -Dtest=ReservationExpiryTest
```

---

## Design Notes

### Concurrency — Pessimistic Locking

The core race condition is: two clients simultaneously see `availableQty = 1`, both try to reserve, and inventory goes negative.

**Solution**: `ProductRepository.findByIdWithLock()` issues `SELECT … FOR UPDATE`, which serialises all concurrent reservations for the same product at the database level. Only one transaction holds the lock at a time; others queue behind it. This is the simplest correct approach — no distributed locks, no retry loops, no eventual consistency.

```
Thread A → SELECT FOR UPDATE (acquires lock) → reserve 10 → COMMIT (releases lock)
Thread B → SELECT FOR UPDATE (waits)          → reserve 10 → COMMIT  ← or REJECT if none left
```

The `Product` entity also carries a database-level `CHECK (available_qty >= 0)` constraint as a hard safety net.

### Reservation Expiry

Each `PENDING_PAYMENT` order carries an `expiresAt` timestamp set to `now + window`. The `ReservationExpiryScheduler` runs on a configurable fixed-delay poll (default 60 s), finds all overdue orders, and for each:

1. Acquires a write lock on the product row.
2. Adds the reserved quantity back to `availableQty`.
3. Transitions the order to `EXPIRED`.

Each order is expired in its own transaction — a failure on one does not block others.

### Idempotency

Payment confirmation checks the order state before acting:
- `PENDING_PAYMENT` → proceeds normally.
- `CONFIRMED` / `EXPIRED` / `REJECTED` → returns `422 Unprocessable Entity` immediately.

### Transaction Boundaries

| Operation | Isolation | Lock |
|-----------|-----------|------|
| Place order | `READ_COMMITTED` | `SELECT FOR UPDATE` on product |
| Confirm payment | `READ_COMMITTED` | `SELECT FOR UPDATE` on product |
| Expire order | `READ_COMMITTED` | `SELECT FOR UPDATE` on product |
| Read product/order | `READ_COMMITTED` (read-only) | None |

### Observability

- **Correlation ID**: injected by `CorrelationIdFilter` from `X-Correlation-Id` header (or generated); present in every response body and log line via SLF4J MDC.
- **Prometheus metrics**: `orders.pending`, `orders.confirmed`, `orders.rejected`, `orders.expired` counters; plus live gauges at `/actuator/prometheus`.
- **Structured logging**: every state transition logs `orderId`, `productId`, `qty`, and relevant timestamps.

### Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `server.port` | `PORT` | `8080` | HTTP listener port |
| `app.reservation.window-minutes` | `RESERVATION_WINDOW_MINUTES` | `15` | How long a reservation stays valid |
| `app.reservation.cleanup-interval-ms` | `RESERVATION_CLEANUP_INTERVAL_MS` | `60000` | Expiry scheduler poll interval |

---

## Assumptions

1. Single-node deployment — pessimistic DB locking is sufficient; a distributed solution would require Redis-based distributed locks or a message queue, which is out of scope.
2. H2 in-memory database is intentional for the case study; a production deployment would use PostgreSQL with the same schema (H2 is set to `MODE=PostgreSQL`).
3. Authentication/authorisation is out of scope.
4. The `clientRef` field on orders supports client-side duplicate detection but server-side deduplication is not implemented (out of scope).
