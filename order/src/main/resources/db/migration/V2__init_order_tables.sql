-- order 도메인 소유 테이블 (Flyway 전환 — decisions.md 25번). 버전 체계는 V1(product) 주석 참고.
-- order_key UNIQUE = 비동기 주문 멱등 처리의 핵심 제약 (decisions.md 8번) — 스키마가 코드 밖
-- 마이그레이션으로 고정되면서 이 제약이 우연(ddl-auto)이 아니라 명시가 됐다.

CREATE TABLE IF NOT EXISTS orders (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    order_key  VARCHAR(36) DEFAULT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     ENUM ('COMPLETED', 'CREATED', 'FAILED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_key (order_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
