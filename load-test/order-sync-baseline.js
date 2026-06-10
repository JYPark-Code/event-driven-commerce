// 동기 주문 API 기준선 측정.
// 오픈 모델(constant-arrival-rate) 사용: 서버가 느려져도 요청 도착률을 유지해야
// "초당 N건이 들어올 때 시스템이 버티는가"를 측정할 수 있다 (closed 모델은 coordinated omission으로 수치가 낙관됨).
//
// 실행: k6 run -e RATE=1000 -e DURATION=30s load-test/order-sync-baseline.js
import http from 'k6/http';
import { check } from 'k6';

const RATE = Number(__ENV.RATE || 100);       // 목표 도착률 (req/s)
const DURATION = __ENV.DURATION || '30s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        order_sync: {
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
        `${BASE_URL}/api/orders`,
        JSON.stringify({ productId: 1, quantity: 1 }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    check(res, { 'status 201': (r) => r.status === 201 });
}
