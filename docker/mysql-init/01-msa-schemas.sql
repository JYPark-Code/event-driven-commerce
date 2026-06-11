-- MSA 3b-2: 서비스별 스키마 분리 (docs/decisions.md 21번)
-- 새 볼륨으로 컨테이너를 처음 띄울 때 자동 실행된다 (docker-entrypoint-initdb.d).
-- 기존 볼륨에는 적용되지 않으므로 한 번 수동 실행 필요:
--   docker exec -i tps-mysql mysql -uroot -proot1234 < docker/mysql-init/01-msa-schemas.sql
--
-- tps1000(단일 스키마)은 그대로 둔다 — main(모놀리스)과 조합 앱(app, 테스트 하네스)이 사용.

CREATE DATABASE IF NOT EXISTS tps_product CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS tps_order CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS tps_backoffice CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON tps_product.* TO 'tps'@'%';
GRANT ALL PRIVILEGES ON tps_order.* TO 'tps'@'%';
GRANT ALL PRIVILEGES ON tps_backoffice.* TO 'tps'@'%';
FLUSH PRIVILEGES;
