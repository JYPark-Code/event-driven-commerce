// 비동기(Kafka) 주문 API 측정 — 동기 기준선(order-sync-baseline.js)과 동일 방법론.
// 측정 대상은 "접수 경로"(202 응답까지). 컨슈머 처리 완료(드레인)는 별도로 DB COUNT로 확인해 기록한다.
//
// 실행: k6 run -e RATE=1000 -e DURATION=30s load-test/order-async.js
import http from 'k6/http';
import { check } from 'k6';

const RATE = Number(__ENV.RATE || 100);       // 목표 도착률 (req/s)
const DURATION = __ENV.DURATION || '30s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        order_async: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: Math.min(RATE, 500),
            maxVUs: 2000,   // 응답 지연 시 VU 부족으로 도착률이 깨지지 않도록 여유
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const res = http.post(
        `${BASE_URL}/api/orders/async`,
        JSON.stringify({ productId: 1, quantity: 1 }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    check(res, { 'status 202': (r) => r.status === 202 });
}
