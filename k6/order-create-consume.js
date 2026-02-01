import http from 'k6/http';
import { check, sleep } from 'k6';

const totalIterations = Number(__ENV.TOTAL_ORDERS || 50);
const vus = Number(__ENV.VUS || 5);

export const options = {
  vus,
  iterations: totalIterations,
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  return { since: new Date().toISOString() };
}

export default function (data) {
  const id = `${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    userId: 1,
    amount: 88.0,
    clientRequestId: `k6-consume-${id}`,
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
      idempotency_key: `k6-consume-${id}`,
    },
  };

  const res = http.post('http://localhost:8081/orders', payload, params);
  check(res, {
    'create status 200': (r) => r.status === 200,
    'create success true': (r) => r.json('success') === true,
  });
  sleep(0.05);
}

export function teardown(data) {
  let count = 0;
  for (let i = 0; i < 10; i += 1) {
    sleep(1);
    const res = http.get(
      `http://localhost:8081/orders/consumes/count?consumerGroup=order-service-group&since=${encodeURIComponent(
        data.since,
      )}`,
    );
    count = res.json('data.count');
    if (count >= totalIterations) {
      break;
    }
  }
  check({ count }, {
    'consume count ok': (v) => v.count >= totalIterations,
  });
}
