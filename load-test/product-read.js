// 상품 조회 측정 (축 2). 핫키 편중 읽기 — 캐시 효과는 인기 상품 반복 조회에서 나온다.
// setup()에서 상품 N개를 만들고, 요청의 80%는 상위 10개(핫키), 20%는 전체에서 균등 선택.
//
// 비교 방법: 캐시 없는 베이스라인은 커밋 6237e30(축 2 - 1단계)에서 같은 스크립트로 측정한다.
//   git worktree add ../tps_1000-baseline 6237e30
//
// 실행: k6 run -e RATE=1000 -e DURATION=30s load-test/product-read.js
import http from 'k6/http';
import { check } from 'k6';

const RATE = Number(__ENV.RATE || 100);       // 목표 도착률 (req/s)
const DURATION = __ENV.DURATION || '30s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCTS = Number(__ENV.PRODUCTS || 100);
const HOT_KEYS = 10;        // 핫키 개수 (상위 10%)
const HOT_RATIO = 0.8;      // 핫키로 가는 요청 비율

export const options = {
    scenarios: {
        product_read: {
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

// 측정 대상 상품을 미리 생성하고 id 목록을 본 시나리오에 넘긴다.
export function setup() {
    const ids = [];
    for (let i = 0; i < PRODUCTS; i++) {
        const res = http.post(
            `${BASE_URL}/api/products`,
            JSON.stringify({ name: `부하측정-${i}`, price: 1000 + i }),
            { headers: { 'Content-Type': 'application/json' } },
        );
        ids.push(res.json('productId'));
    }
    return { ids };
}

export default function (data) {
    const ids = data.ids;
    const idx = Math.random() < HOT_RATIO
        ? Math.floor(Math.random() * HOT_KEYS)              // 핫키: 상위 10개
        : Math.floor(Math.random() * ids.length);           // 콜드 포함 균등
    const res = http.get(`${BASE_URL}/api/products/${ids[idx]}`);
    check(res, { 'status 200': (r) => r.status === 200 });
}
