# 자율 개발 로그 (multi-model harness)

멀티모델 하네스(GPT 기획 → Claude 비평 → Fable 구현 → Opus 리뷰 → 결정론적 테스트 게이트)가
이 레포(msa)에 구현한 기능을 한 건씩 기록한다. 기능마다: 파이프라인 동작 · 구현 요약 ·
검증 결과 · 비고. 모든 기능은 게이트(전체 테스트 green) 통과 후에만 머지된다.

---

## #1 — 주문 목록 조회 API (페이징·상태 필터)

- **날짜**: 2026-06-17
- **반영**: PR #1 → `msa` (`09b0cc1`)
- **엔드포인트**: `GET /api/orders?page=&size=&status=`
- **파이프라인**: 기획 2 rev · 비평 1 · 리뷰 1턴 · dev 루프 1 · ~6분

### 구현 요약
- `OrderController#listOrders` — `page`(기본 0) / `size`(기본 20, 최대 100 클램프) / `status`(선택)
- `OrderService#listOrders` — 검증(`page<0`·`size<1`→400), `status` 정확 매칭(미정의→400), size 클램프
- 정렬 `createdAt` 내림차순 + `id` 보조키로 페이지 간 안정화
- `OrderRepository#findByStatus(status, Pageable)` 추가
- 응답 DTO `OrderPageResponse` (content + page/size/totalElements/totalPages)
- 읽기 전용 — 스키마/마이그레이션 변경 없음, 기존 엔드포인트 계약 불변

### 검증
- 게이트 green (기존 회귀 0건 + 신규 테스트 통과)
- 모델 자체 작성 `OrderListApiTest` 11케이스: 기본 페이징 / 상태 필터 / size 클램프 /
  전체 초과 page(빈 content) / 정렬 불변식 / 잘못된 status·page·size(400) / 메서드(405)
- 통합 DB 공유·롤백 없음을 고려해 절대 카운트 대신 상대 단언으로 견고화

### 비고
- 읽기 전용 추가형 기능 — 하네스가 안정적으로 처리. 정산 페이징의 "안정 정렬(보조키)" 패턴을 스스로 차용.

---

## #2 — 주문 취소 API (상태 머신 + 스키마 마이그레이션)

- **날짜**: 2026-06-17
- **반영**: PR #2 → `msa`
- **엔드포인트**: `POST /api/orders/{orderId}/cancel`
- **파이프라인**: 기획 1 rev · 비평 0 · 리뷰 1턴 · dev 루프 1

### 구현 요약
- `OrderController#cancelOrder` — CREATED만 취소(200 + 갱신 OrderResponse)
- `OrderService#cancelOrder` — 미존재→404(EntityNotFoundException), 취소 불가 상태→409(ResponseStatusException)
- `Order#isCancelable()` + `cancel()` — 가드/전이 분리
- `OrderStatus`에 `CANCELED` 추가
- **Flyway V4 마이그레이션**: `ALTER TABLE orders MODIFY status ENUM('COMPLETED','CREATED','FAILED','CANCELED')` —
  스키마는 Flyway 소유(ddl-auto=validate)라 enum 값 추가 시 마이그레이션 필수

### 검증
- 게이트 green (실 MySQL에 V4 적용 + CANCELED 영속 테스트 통과)
- `OrderCancelApiTest` 5케이스: CREATED→200(+DB 재조회로 CANCELED 영속 단언) /
  COMPLETED·FAILED·이미CANCELED→409(DB 상태 불변 단언) / 미존재→404

### 비고
- **상태 변경 + 스키마 진화 기능**. 영속 enum 추가 → Flyway 마이그레이션 필요가 핵심 함정인데,
  spec에 명시하니 하네스가 V4를 정확히 추가하고 DB ENUM까지 확장해 end-to-end로 동작.
- (참고) 동일 기능을 마이그레이션 가이드 없이 eval로 돌렸을 땐 마이그레이션을 빠뜨려 CANCELED
  저장이 'Data truncated'로 실패했었다 — 추가형 vs 스키마 변경 기능의 난이도 차이를 보여주는 대비.
