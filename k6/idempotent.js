import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 1000,
  duration: '10s',
  thresholds: {
    http_req_failed: ['rate<0.001'],
  },
};

export default function () {
  const res = http.post('http://localhost:8080/demo/idempotent', null, {
    headers: { idempotency_key: 'k6-demo' },
  });
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
