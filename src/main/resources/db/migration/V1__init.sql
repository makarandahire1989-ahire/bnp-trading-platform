CREATE TABLE products (
    id              UUID        NOT NULL DEFAULT RANDOM_UUID(),
    name            VARCHAR(255) NOT NULL,
    total_quantity  INT         NOT NULL,
    available_qty   INT         NOT NULL,
    reserved_qty    INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT chk_total_qty     CHECK (total_quantity > 0),
    CONSTRAINT chk_available_qty CHECK (available_qty >= 0),
    CONSTRAINT chk_reserved_qty  CHECK (reserved_qty  >= 0),
    CONSTRAINT chk_inventory_sum CHECK (available_qty + reserved_qty <= total_quantity)
);

CREATE TABLE orders (
    id              UUID        NOT NULL DEFAULT RANDOM_UUID(),
    product_id      UUID        NOT NULL,
    quantity        INT         NOT NULL,
    status          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP,
    paid_at         TIMESTAMP,
    correlation_id  UUID        NOT NULL,
    client_ref      VARCHAR(255),
    CONSTRAINT pk_orders         PRIMARY KEY (id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_quantity      CHECK (quantity > 0),
    CONSTRAINT chk_status        CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','REJECTED','EXPIRED'))
);

CREATE INDEX idx_orders_status     ON orders(status);
CREATE INDEX idx_orders_product_id ON orders(product_id);
CREATE INDEX idx_orders_expires_at ON orders(expires_at);
