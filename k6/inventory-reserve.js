import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const successCount = new Counter('reserve_success_count');
const failureCount = new Counter('reserve_failure_count');

export const options = {
  scenarios: {
    reserve_stock: {
      executor: 'per-vu-iterations',
      vus: 1000,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    reserve_success_count: ['count<=100'],
  },
};

export default function () {
  const orderNo = `K6-${__VU}`;
  const payload = JSON.stringify({
    orderNo,
    skuId: 1001,
    warehouseId: 1,
    qty: 1,
    clientRequestId: `k6-${orderNo}`,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      idempotency_key: `k6-${orderNo}`,
    },
  };

  const res = http.post('http://localhost:8082/inventory/reservations/try', payload, params);
  const success = res.json('success');
  if (success) {
    successCount.add(1);
  } else {
    failureCount.add(1);
  }

  check(res, {
    'http status ok': (r) => r.status === 200,
    'success or insufficient': (r) => {
      const code = r.json('code');
      return r.json('success') === true || code === 'INV-409';
    },
  });
}
