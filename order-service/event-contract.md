# Order Service Event Contract (v1)

## Topic
- `order-events`

## Message Key
- `event_id`（Outbox 事件 ID）

## Tags
| Tag | Description | Version |
| --- | --- | --- |
| `OrderEvent` | 订单事件（OrderCreated/OrderCanceled） | v1 |

## Payload Schema (JSON)

```json
{
  "eventId": "string",
  "eventType": "OrderCreated | OrderCanceled",
  "orderNo": "string",
  "userId": 0,
  "amount": 0.00,
  "status": "CREATED | CANCELED",
  "clientRequestId": "string",
  "occurredAt": "2024-01-01T00:00:00Z",
  "schemaVersion": "v1",
  "items": [
    {
      "skuId": 0,
      "qty": 0,
      "titleSnapshot": "string",
      "priceSnapshot": 0.00,
      "promoSnapshotJson": "{}"
    }
  ]
}
```

## Delivery Semantics
- Outbox + relay at-least-once.
- Consumers must implement idempotency by message key.
