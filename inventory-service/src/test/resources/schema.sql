CREATE TABLE t_inventory (
    sku_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    available_qty INT NOT NULL,
    reserved_qty INT NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE (sku_id, warehouse_id)
);

CREATE TABLE t_inventory_reservation (
    order_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    qty INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE (order_no, sku_id, warehouse_id)
);

CREATE TABLE t_inventory_txn (
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

CREATE TABLE t_inventory_state_flow (
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

CREATE TABLE t_outbox_event (
    event_id VARCHAR(64) PRIMARY KEY,
    biz_key VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    tag VARCHAR(64) NOT NULL,
    payload_json CLOB NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    trace_id VARCHAR(64)
);

CREATE TABLE t_mq_consume_log (
    event_id VARCHAR(64) NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (event_id)
);
