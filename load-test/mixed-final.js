// 종합 혼합 부하 (축 4 최종): 상품 조회 80% + 비동기 주문 20%를 동시에 건다.
// 단일 엔드포인트 측정(1-a~2)과 달리 "실서비스形 트래픽 믹스"에서 1000 TPS를 버티는지 보는 것이 목적.
// 시나리오별 p95는 thresholds의 서브 메트릭으로 분리 집계된다.
//
// 실행: k6 run -e TOTAL_RATE=1000 -e DURATION=30s load-test/mixed-final.js
import http from 'k6/http';
import { check } from 'k6';

const TOTAL_RATE = Number(__ENV.TOTAL_RATE || 100);   // 두 시나리오 합산 도착률 (req/s)
const READ_RATIO = Number(__ENV.READ_RATIO || 0.8);   // 조회 비중 (나머지가 주문)
const DURATION = __ENV.DURATION || '30s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCTS = Number(__ENV.PRODUCTS || 100);
const HOT_KEYS = 10;        // product-read.js와 동일한 핫키 분포 (80%가 상위 10개로)
const HOT_RATIO = 0.8;

const readRate = Math.round(TOTAL_RATE * READ_RATIO);
const orderRate = TOTAL_RATE - readRate;

export const options = {
    scenarios: {
        product_read: {
            executor: 'constant-arrival-rate',
            exec: 'productRead',
            rate: readRate,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: Math.min(readRate, 500),
            maxVUs: 2000,
        },
        order_async: {
            executor: 'constant-arrival-rate',
            exec: 'orderAsync',
            rate: orderRate,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: Math.min(orderRate, 300),
            maxVUs: 1000,
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        // 서브 메트릭 분리 집계용 — 임계값 자체는 느슨하게 (측정이 목적, 합격선 강제가 아님)
        'http_req_duration{scenario:product_read}': ['p(95)<5000'],
        'http_req_duration{scenario:order_async}': ['p(95)<5000'],
        'dropped_iterations{scenario:product_read}': ['count>=0'],
        'dropped_iterations{scenario:order_async}': ['count>=0'],
    },
};

// 조회 대상 상품을 미리 만들어 두 시나리오가 같은 상품 풀을 쓰게 한다 (주문도 실존 상품으로).
export function setup() {
    const ids = [];
    for (let i = 0; i < PRODUCTS; i++) {
        const res = http.post(
            `${BASE_URL}/api/products`,
            JSON.stringify({ name: `혼합측정-${i}`, price: 1000 + i }),
            { headers: { 'Content-Type': 'application/json' } },
        );
        ids.push(res.json('productId'));
    }
    return { ids };
}

export function productRead(data) {
    const ids = data.ids;
    const idx = Math.random() < HOT_RATIO
        ? Math.floor(Math.random() * HOT_KEYS)
        : Math.floor(Math.random() * ids.length);
    const res = http.get(`${BASE_URL}/api/products/${ids[idx]}`);
    check(res, { 'read 200': (r) => r.status === 200 });
}

export function orderAsync(data) {
    const ids = data.ids;
    const productId = ids[Math.floor(Math.random() * ids.length)];
    const res = http.post(
        `${BASE_URL}/api/orders/async`,
        JSON.stringify({ productId: productId, quantity: 1 }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    check(res, { 'order 202': (r) => r.status === 202 });
}
