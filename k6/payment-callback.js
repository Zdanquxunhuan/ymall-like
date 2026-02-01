import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';

export const options = {
  vus: 1,
  iterations: 1,
};

const BASE_ORDER = __ENV.ORDER_BASE_URL || 'http://localhost:8081';
const BASE_PAYMENT = __ENV.PAYMENT_BASE_URL || 'http://localhost:8084';
const CALLBACK_SECRET = __ENV.PAYMENT_CALLBACK_SECRET || 'demo-secret';

function hmacSignature(payload) {
  return crypto.hmac('sha256', CALLBACK_SECRET, payload, 'hex');
}

export default function () {
  const createOrderPayload = {
    userId: 1,
    amount: 88.0,
    clientRequestId: 'order-pay-1',
    items: [
      {
        skuId: 1001,
        qty: 1,
        titleSnapshot: '测试商品',
        priceSnapshot: 88.0,
        promoSnapshotJson: '{}',
      },
    ],
  };

  const orderResp = http.post(`${BASE_ORDER}/orders`, JSON.stringify(createOrderPayload), {
    headers: {
      'Content-Type': 'application/json',
      idempotency_key: 'order-pay-1',
    },
  });
  check(orderResp, { 'order created': (r) => r.status === 200 });
  const orderNo = orderResp.json('data.orderNo');

  let orderStatus = orderResp.json('data.status');
  for (let i = 0; i < 10 && orderStatus !== 'STOCK_RESERVED'; i++) {
    sleep(0.5);
    const orderQuery = http.get(`${BASE_ORDER}/orders/${orderNo}`);
    orderStatus = orderQuery.json('data.status');
  }

  const amountValue = 88.0;
  const amountSignature = amountValue.toFixed(1);
  const createPaymentPayload = {
    orderNo,
    amount: amountValue,
    channel: 'MOCK',
    clientRequestId: 'payment-create-1',
  };
  const payResp = http.post(`${BASE_PAYMENT}/payments`, JSON.stringify(createPaymentPayload), {
    headers: {
      'Content-Type': 'application/json',
      idempotency_key: 'payment-create-1',
    },
  });
  check(payResp, { 'payment created': (r) => r.status === 200 });
  const payNo = payResp.json('data.payNo');

  const callbackPayload = {
    payNo,
    orderNo,
    amount: amountValue,
    status: 'SUCCESS',
    clientRequestId: 'callback-1',
  };
  const signPayload = `${payNo}|${orderNo}|${amountSignature}|SUCCESS`;
  const signature = hmacSignature(signPayload);

  for (let i = 0; i < 1000; i++) {
    const callbackResp = http.post(`${BASE_PAYMENT}/payments/mock-callback`, JSON.stringify(callbackPayload), {
      headers: {
        'Content-Type': 'application/json',
        'X-Signature': signature,
        idempotency_key: 'callback-1',
      },
    });
    if (i === 0) {
      check(callbackResp, { 'callback accepted': (r) => r.status === 200 });
    }
  }

  sleep(1);
  const payQuery = http.get(`${BASE_PAYMENT}/payments/${payNo}`);
  const orderQuery = http.get(`${BASE_ORDER}/orders/${orderNo}`);

  check(payQuery, { 'payment success': (r) => r.json('data.status') === 'SUCCESS' });
  check(orderQuery, { 'order paid': (r) => r.json('data.status') === 'PAID' });
}
