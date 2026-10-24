CREATE TABLE orders_order (
    id            uuid          NOT NULL,
    customer_id   uuid          NOT NULL,
    created_at    timestamptz   NOT NULL,
    currency      char(3)       NOT NULL,
    total_amount  numeric(19,2) NOT NULL,
    item_count    int           NOT NULL,
    version       bigint        NOT NULL,
    CONSTRAINT pk_orders_order PRIMARY KEY (id),
    CONSTRAINT ck_orders_order_item_count CHECK (item_count BETWEEN 1 AND 50),
    CONSTRAINT ck_orders_order_total_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_order_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_orders_order_customer_page
    ON orders_order (customer_id, created_at DESC, id DESC);

CREATE TABLE orders_order_line (
    order_id           uuid          NOT NULL,
    line_number        int           NOT NULL,
    product_id         uuid          NOT NULL,
    product_name       varchar(200)  NOT NULL,
    unit_price_amount  numeric(19,2) NOT NULL,
    quantity           int           NOT NULL,
    line_total_amount  numeric(19,2) NOT NULL,
    CONSTRAINT pk_orders_order_line PRIMARY KEY (order_id, line_number),
    CONSTRAINT fk_orders_order_line_order FOREIGN KEY (order_id) REFERENCES orders_order (id),
    CONSTRAINT ck_orders_order_line_line_number CHECK (line_number >= 1),
    CONSTRAINT ck_orders_order_line_quantity CHECK (quantity BETWEEN 1 AND 100),
    CONSTRAINT ck_orders_order_line_unit_price_amount CHECK (unit_price_amount >= 0),
    CONSTRAINT ck_orders_order_line_line_total CHECK (line_total_amount = unit_price_amount * quantity),
    CONSTRAINT uq_orders_order_line_order_product UNIQUE (order_id, product_id)
);
