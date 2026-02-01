# Flowchart

## Trace 链路

```mermaid
flowchart TD
    A[HTTP Request] --> B[TraceIdFilter]
    B --> C[MDC traceId]
    C --> D[Controller]
    D --> E[Service/Repo/MQ]
    E --> F[Log with traceId]
    E --> G[Response Header X-Trace-Id]
```

## 幂等流程

```mermaid
flowchart TD
    A[Client with idempotency_key] --> B[IdempotencyService]
    B --> C{Redis key exists?}
    C -- No --> D[Set PENDING + TTL]
    D --> E[Execute business]
    E --> F[Cache result JSON]
    C -- Yes --> G{Result ready?}
    G -- Yes --> H[Return cached result]
    G -- Pending --> I[Short wait & retry]
    I --> G
```

## 限流流程

```mermaid
flowchart TD
    A[Request] --> B[RateLimiter]
    B --> C[Redis Lua Token Bucket]
    C --> D{Allowed?}
    D -- Yes --> E[Continue]
    D -- No --> F[Return RATE-429]
```

## 订单创建/取消流程（含幂等 + Outbox）

```mermaid
flowchart TD
    A[POST /orders] --> B[Resolve idempotency_key/clientRequestId]
    B --> C{Rate limit pass?}
    C -- No --> C1[Return RATE-429]
    C -- Yes --> D[IdempotencyService execute]
    D --> E{Order exists?}
    E -- Yes --> F[Return existing order]
    E -- No --> G[Insert t_order + t_order_item]
    G --> H[Insert t_order_state_flow CREATED]
    H --> I[Insert t_outbox_event OrderCreated]
    I --> J[Return order]

    K[POST /orders/{orderNo}/cancel] --> L[Resolve idempotency_key/clientRequestId]
    L --> M{Rate limit pass?}
    M -- No --> M1[Return RATE-429]
    M -- Yes --> N[IdempotencyService execute]
    N --> O{Status == CREATED?}
    O -- No --> P[Return error or already canceled]
    O -- Yes --> Q[CAS update status -> CANCELED]
    Q --> R[Insert t_order_state_flow CANCELED]
    R --> S[Insert t_outbox_event OrderCanceled]
    S --> T[Return order]
```

## 订单状态机

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> CANCELED: cancel (CAS)
```

## Outbox Relay

```mermaid
flowchart TD
    A[Outbox Relay Scheduler] --> B[Query t_outbox_event NEW/FAILED]
    B --> C[Send RocketMQ message]
    C --> D{Send success?}
    D -- Yes --> E[Mark SENT]
    D -- No --> F[Mark FAILED + next_retry_at]
```
