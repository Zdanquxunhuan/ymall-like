import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 300,
  duration: '10s',
  thresholds: {
    http_req_failed: ['rate<0.1'],
  },
};

export default function () {
  const res = http.get('http://localhost:8080/demo/ratelimit', {
    headers: { 'X-User-Id': 'k6-user' },
  });
  check(res, {
    'rate limited returns code': (r) => r.status === 200 && r.body.includes('RATE-429'),
  });
}
