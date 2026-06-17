-- product 도메인 소유 테이블 (Flyway 전환 — decisions.md 25번).
-- DDL은 Hibernate(ddl-auto: update)가 만들어 둔 실제 스키마(SHOW CREATE TABLE)를 기준으로 고정.
-- IF NOT EXISTS: 기존 데모 DB(이미 테이블 존재)에 baseline-on-migrate로 얹을 때 멱등.
-- 버전 번호는 모듈별로 분리(V1 product / V2 order / V3 backoffice) — 조합 앱(app)은
-- 세 모듈의 마이그레이션을 한 클래스패스에서 모두 보므로 번호가 겹치면 Flyway가 실패한다.

CREATE TABLE IF NOT EXISTS products (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    price      BIGINT       NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
