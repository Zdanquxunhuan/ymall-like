import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    create_orders: {
      executor: 'constant-arrival-rate',
      rate: 500,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const id = `${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    userId: 1,
    amount: 88.0,
    clientRequestId: `k6-${id}`,
    items: [
      {
        skuId: 1001,
        qty: 1,
        titleSnapshot: 'k6商品',
        priceSnapshot: 88.0,
        promoSnapshotJson: '{}',
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      idempotency_key: `k6-${id}`,
    },
  };

  const res = http.post('http://localhost:8081/orders', payload, params);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'success true': (r) => r.json('success') === true,
  });
  sleep(0.1);
}
