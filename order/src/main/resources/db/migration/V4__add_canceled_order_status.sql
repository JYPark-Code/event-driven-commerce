-- order 도메인: 주문 취소 기능(POST /api/orders/{orderId}/cancel)을 위해 status ENUM에 CANCELED 추가.
-- 스키마는 Flyway가 소유하고(ddl-auto=validate) 세 모듈이 한 클래스패스를 공유하므로 버전은 순차(V4).
-- Java enum에만 CANCELED를 추가하면 저장 시 'Data truncated for column status'로 실패하므로
-- DB ENUM 정의를 함께 확장한다. 기존 값/제약은 유지하고 'CANCELED'만 더한다.

ALTER TABLE orders
    MODIFY status ENUM ('COMPLETED', 'CREATED', 'FAILED', 'CANCELED') NOT NULL;
