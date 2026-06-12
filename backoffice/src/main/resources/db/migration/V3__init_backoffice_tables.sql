-- backoffice 도메인 소유 테이블 (Flyway 전환 — decisions.md 25번). 버전 체계는 V1(product) 주석 참고.
-- BATCH_* 메타 테이블은 여기 없다 — Spring Batch 자체 초기화(spring.batch.jdbc.initialize-schema)가
-- 버전별 공식 DDL을 관리하므로 중복 정의하지 않는다.

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6)  NOT NULL,
    password   VARCHAR(60)  NOT NULL,
    role       ENUM ('ADMIN', 'USER') NOT NULL,
    username   VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settlements (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    created_at       DATETIME(6)  NOT NULL,
    product_id       BIGINT       NOT NULL,
    product_name     VARCHAR(100) NOT NULL,
    settlement_month VARCHAR(7)   NOT NULL,
    total_amount     BIGINT       NOT NULL,
    total_quantity   BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlements_month_product (settlement_month, product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_replica (
    product_id BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    price      BIGINT       NOT NULL,
    synced_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
