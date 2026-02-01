CREATE TABLE IF NOT EXISTS outbox_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    tag VARCHAR(64) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64),
    version BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_order (
    order_no VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_order_user_request (user_id, client_request_id)
);

CREATE TABLE IF NOT EXISTS t_order_item (
    order_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    qty INT NOT NULL,
    title_snapshot VARCHAR(128) NOT NULL,
    price_snapshot DECIMAL(18, 2) NOT NULL,
    promo_snapshot_json TEXT,
    PRIMARY KEY (order_no, sku_id)
);

CREATE TABLE IF NOT EXISTS t_order_state_flow (
    order_no VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    event VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),
    ignored_reason VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_pay_order (
    pay_no VARCHAR(64) PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_pay_order_no (order_no),
    UNIQUE KEY uk_pay_no (pay_no)
);

CREATE TABLE IF NOT EXISTS t_pay_callback_log (
    pay_no VARCHAR(64) NOT NULL,
    raw_payload TEXT NOT NULL,
    signature_valid TINYINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_pay_state_flow (
    pay_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    event VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),
    ignored_reason VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_outbox_event (
    event_id VARCHAR(64) PRIMARY KEY,
    biz_key VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    tag VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    trace_id VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS t_mq_consume_log (
    event_id VARCHAR(64) NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_id (event_id)
);

CREATE TABLE IF NOT EXISTS t_inventory (
    sku_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    available_qty INT NOT NULL,
    reserved_qty INT NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_inventory_sku_wh (sku_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS t_inventory_reservation (
    order_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    qty INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_inventory_reservation (order_no, sku_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS t_inventory_txn (
    txn_id VARCHAR(64) PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    delta_available INT NOT NULL,
    delta_reserved INT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_inventory_state_flow (
    order_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    event VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO t_inventory (sku_id, warehouse_id, available_qty, reserved_qty)
VALUES (1001, 1, 100, 0)
ON DUPLICATE KEY UPDATE available_qty = VALUES(available_qty), reserved_qty = VALUES(reserved_qty);
