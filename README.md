# YMall-like Platform

## 快速开始

```bash
docker compose up -d
```

```bash
mvn -q -pl demo-service -am spring-boot:run
mvn -q -pl order-service -am spring-boot:run
mvn -q -pl inventory-service -am spring-boot:run
```

> 需要 JDK17 + Maven。

## 核心能力

- 统一返回体 `Result<T>` + `traceId`
- 统一错误码 + 业务异常 + 全局异常处理
- traceId 生成与透传（Filter + MDC + 响应头）
- MyBatis-Plus 全局配置（乐观锁/逻辑删除/自动填充/分页）
- 幂等组件（Redis 实现）
- 限流组件（Redis Lua 令牌桶）
- RocketMQ 模板 + Outbox 中继骨架
- Transactional Outbox Relay（指数退避 + DLQ + 消费幂等日志）
- Redis Lua 库存预扣 + 订单事件驱动库存预占

## curl 示例

### 订单创建

```bash
curl -X POST http://localhost:8081/orders \
  -H 'Content-Type: application/json' \
  -H 'idempotency_key: order-req-1' \
  -d '{
    "userId": 1,
    "amount": 88.00,
    "clientRequestId": "order-req-1",
    "items": [
      {
        "skuId": 1001,
        "qty": 1,
        "titleSnapshot": "测试商品",
        "priceSnapshot": 88.00,
        "promoSnapshotJson": "{}"
      }
    ]
  }'
```

### Outbox Relay 与消费日志（1s 内可见）

```bash
curl -X POST http://localhost:8081/orders \
  -H 'Content-Type: application/json' \
  -H 'idempotency_key: order-req-2' \
  -d '{
    "userId": 1,
    "amount": 88.00,
    "clientRequestId": "order-req-2",
    "items": [
      {
        "skuId": 1002,
        "qty": 1,
        "titleSnapshot": "测试商品",
        "priceSnapshot": 88.00,
        "promoSnapshotJson": "{}"
      }
    ]
  }'
```

1s 内可在 `order-service` 日志看到 outbox relay 与 `OrderCreated` 消费日志。

### 可靠性演示（停止 broker 后恢复）

```bash
docker compose stop rocketmq-broker
```

此时创建订单会看到 outbox relay 重试日志。随后启动 broker：

```bash
docker compose start rocketmq-broker
```

恢复后会看到之前事件成功投递与消费日志（retry 生效）。

### 消费计数接口

```bash
curl "http://localhost:8081/orders/consumes/count?consumerGroup=order-service-group&since=2024-01-01T00:00:00Z"
```

用于压测验证消费数量一致。

### 订单查询

```bash
curl http://localhost:8081/orders/Oxxxx
```

### 订单取消

```bash
curl -X POST http://localhost:8081/orders/Oxxxx/cancel \
  -H 'idempotency_key: cancel-1'
```

### 幂等接口

```bash
curl -X POST http://localhost:8080/demo/idempotent \
  -H 'idempotency_key: demo-1'
```

重复请求会返回相同结果。

### 限流接口

```bash
curl http://localhost:8080/demo/ratelimit -H 'X-User-Id: u1'
```

触发限流时返回 `RATE-429` 错误码。

## k6 压测

```bash
k6 run k6/idempotent.js
k6 run k6/ratelimit.js
k6 run k6/order-create.js
k6 run k6/order-create-consume.js
k6 run k6/inventory-reserve.js
```

> `k6/inventory-reserve.js` 使用 1000 并发请求同一 SKU，断言成功数 <= 100，失败返回 `INV-409`。

## 库存事件契约

- **OrderCreated**（order-service -> inventory-service）
  - topic: `order-events`
  - tag: `OrderEvent`
  - key: `eventId`
  - schemaVersion: `v1`
- **StockReserved / StockReserveFailed**（inventory-service -> 下游）
  - topic: `inventory-events`
  - tag: `StockEvent`
  - key: `eventId`
  - schemaVersion: `v1`

## 库存事件乱序/重复演示（order-service）

> 使用接口注入 StockReserved/StockReserveFailed，验证最终状态稳定。

1) 创建订单并记录 orderNo。

2) 发送乱序 + 重复事件：

```bash
ORDER_NO=Oxxxx
EVENT_ID_DUP=demo-dup-1

curl -X POST http://localhost:8081/orders/stock-events/demo \
  -H 'Content-Type: application/json' \
  -d '{
    "orderNo": "'"$ORDER_NO"'",
    "skuId": 1001,
    "warehouseId": 1,
    "qty": 1,
    "events": [
      {
        "eventType": "StockReserveFailed",
        "eventId": "out-of-order-1",
        "eventTime": "2024-01-01T00:00:02Z"
      },
      {
        "eventType": "StockReserved",
        "eventId": "'"$EVENT_ID_DUP"'",
        "eventTime": "2024-01-01T00:00:01Z"
      },
      {
        "eventType": "StockReserved",
        "eventId": "'"$EVENT_ID_DUP"'",
        "eventTime": "2024-01-01T00:00:01Z"
      }
    ]
  }'
```

3) 查询订单最终状态：

```bash
curl http://localhost:8081/orders/$ORDER_NO
```

预期：最终状态稳定为 `STOCK_RESERVED` 或 `STOCK_FAILED`，乱序/重复事件会被记录为 IGNORED（见 t_order_state_flow）。

## 库存初始化与超卖验证

- `sql/init.sql` 默认初始化 `skuId=1001`、`warehouseId=1`、`available=100`。
- `inventory-service` 启动时会将 `t_inventory.available_qty` 预热到 Redis `inv:{warehouseId}:{skuId}`。
- Lua 预扣在 `inv:{warehouseId}:{skuId}` 上原子判断与扣减，防止并发超卖。

## 幂等策略说明（库存预占）

- Redis Lua 使用 `inv:resv:{orderNo}:{warehouseId}:{skuId}` 作为幂等标记，重复消息直接返回 `DUPLICATE`，不再扣减。
- 数据库 `t_inventory_reservation` 通过唯一约束 `(order_no, sku_id, warehouse_id)` 保证幂等落库。

## 库存占用超时释放（框架）

`inventory.reservation-timeout.enabled=true` 后启用扫描任务：

- 扫描 `t_inventory_reservation` 中 `RESERVED` 且超过超时阈值的记录。
- 使用 `releaseReservation` 执行状态流转 `RESERVED -> RELEASED`（CAS）并回补 `t_inventory`。
- 调用 Redis Lua 释放脚本，将 `inv:{warehouseId}:{skuId}` 回补（幂等）。

## 常见故障排查

1. **Redis 连接失败**：确认 `docker compose ps` 中 redis 端口 6379 已启动。
2. **RocketMQ 连接失败**：确认 namesrv 9876 与 broker 10911 端口已开放。
3. **MySQL 连接失败**：确认 root/root 用户可连接，数据库 `ymall_demo` 已创建。
4. **traceId 缺失**：检查 `TraceIdFilter` 是否被扫描到（`scanBasePackages` 包含 `com.ymall.platform`）。
### 库存查询

```bash
curl "http://localhost:8082/inventory/1001?warehouseId=1"
```

### 库存预占（幂等）

```bash
curl -X POST http://localhost:8082/inventory/reservations/try \
  -H 'Content-Type: application/json' \
  -H 'idempotency_key: reserve-1' \
  -d '{
    "orderNo": "O-1000",
    "skuId": 1001,
    "warehouseId": 1,
    "qty": 1,
    "clientRequestId": "reserve-1"
  }'
```

### 订单对应库存占用查询

```bash
curl "http://localhost:8082/inventory/reservations?orderNo=O-1000"
```
