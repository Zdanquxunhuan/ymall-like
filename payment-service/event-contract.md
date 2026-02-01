# Payment Service Event Contract (v1)

## Topic
- `payment-events`

## Message Key
- `event_id`（Outbox 事件 ID）

## Tags
| Tag | Description | Version |
| --- | --- | --- |
| `PaymentEvent` | 支付事件（PaymentSucceeded） | v1 |

## Payload Schema (JSON)

```json
{
  "eventId": "string",
  "eventType": "PaymentSucceeded",
  "payNo": "string",
  "orderNo": "string",
  "amount": 0.00,
  "traceId": "string",
  "schemaVersion": "v1",
  "occurredAt": "2024-01-01T00:00:00Z"
}
```

## Delivery Semantics
- Outbox + relay at-least-once.
- Consumers must implement idempotency by message key.
