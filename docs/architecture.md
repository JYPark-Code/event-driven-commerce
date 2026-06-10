# 아키텍처 (Architecture)

> 전체 구조와 4개 핵심 축. 상세 설계 결정은 [decisions.md](./decisions.md), 측정은 [benchmarks.md](./benchmarks.md).

## 한눈에 보기

```mermaid
flowchart TB
    Client([Client]) -- "HTTP" --> App
    K6["k6 부하테스트<br/>1000 TPS 목표"] -. "부하 주입" .-> App
    App -. "/actuator 메트릭" .-> K6

    subgraph App["Spring Boot 단일 앱 — Java 17 / Boot 3.5.14"]
        subgraph order["order — 주문 처리 (축 1)"]
            SYNC["동기 주문"]
            ASYNC["비동기 접수<br/>(즉시 202)"]
            CONSUMER["Kafka Consumer<br/>배치 리스너 · 멱등 · DLQ·재시도"]
            NOTI["notification<br/>(로그/모의 발송)"]
        end
        subgraph product["product — 캐시 계층화 (축 2)"]
            L1["L1 캐시 (Caffeine)<br/>single-flight · TTL 60s"]
        end
        subgraph back["backoffice (축 3)"]
            RBAC["Spring Security<br/>JWT + RBAC"]
            BATCH["Spring Batch<br/>월별 정산"]
        end
    end

    subgraph Infra["Docker Compose"]
        KAFKA[("Kafka 3.8<br/>KRaft")]
        REDIS[("Redis 7<br/>L2 캐시 · pub/sub")]
        MYSQL[("MySQL 8.0")]
    end

    SYNC -- "INSERT" --> MYSQL
    ASYNC -- "publish" --> KAFKA
    KAFKA -- "consume (배치)" --> CONSUMER
    CONSUMER -- "batch INSERT" --> MYSQL
    CONSUMER --> NOTI
    L1 -- "miss" --> REDIS
    REDIS -- "miss" --> MYSQL
    REDIS -. "L1 무효화 pub/sub" .-> L1
    BATCH -- "orders 집계 (GROUP BY)" --> MYSQL
```

## 비동기 주문 흐름 (축 1 핵심)

```mermaid
sequenceDiagram
    participant C as Client
    participant API as 주문 API
    participant K as Kafka
    participant CS as Consumer (배치 리스너)
    participant DB as MySQL

    C->>API: POST /api/orders/async
    API->>K: OrderCreatedEvent 발행 (orderKey = UUID)
    API-->>C: 202 Accepted + orderKey — p95 ≈ 1.6ms
    K->>CS: 배치 poll (최대 500건)
    CS->>DB: 기처리 필터 SELECT(IN) + multi-row INSERT IGNORE
    Note over CS,DB: 멱등성 = 존재 확인(1차) + order_key 유니크 제약(2차)
    CS->>CS: 후속 처리(알림) → COMPLETED
    Note over K,CS: 실패 시 1초 백오프 × 2회 재시도 → order.created.dlq
    C->>API: GET /api/orders/async/{orderKey} (폴링)
    API-->>C: 200 COMPLETED / 404 (처리 전)
```

## 모듈(패키지) 구조
```
com.jypark.tps1000
 ├─ order        # 축 1: 주문 — 동기 / Kafka 비동기, DLQ·재시도, 멱등성
 ├─ product      # 축 2: 상품 조회 — L1(Caffeine)/L2(Redis)/MySQL 캐시 계층화
 ├─ backoffice   # 축 3: RBAC(Spring Security) + 정산 배치(Spring Batch)
 └─ common       # 공통 설정, 예외, 응답 포맷 등
```

## 4개 핵심 축
1. **주문 처리** — 동기 vs Kafka 비동기를 둘 다 구현해 TPS·응답시간 비교 측정. 비동기는 접수 즉시 응답 + 컨슈머 후속처리. DLQ·재시도·멱등(주문ID).
2. **상품 조회 캐시 계층화** — L1(Caffeine) → L2(Redis) → MySQL. 무효화 전략, L1/L2 정합성, Cache Stampede 대응.
3. **백오피스** — Spring Security RBAC(관리자/일반 분리) + Spring Batch 월별 정산 잡 1개.
4. **부하테스트** — k6로 핵심 엔드포인트(주문)에 1000 TPS 목표. 시나리오·수치·병목을 benchmarks.md에 기록.

## 인프라 (docker-compose.yml)
| 서비스 | 이미지 | 호스트 포트 | 용도 |
|---|---|---|---|
| MySQL | mysql:8.0 | 3307 | 영속 저장소 (3306은 로컬 mysqld 점유로 회피) |
| Redis | redis:7-alpine | 16379 | L2 캐시 / 세션 (AOF) — 6379는 Windows 예약 포트 범위라 회피 |
| Kafka | apache/kafka:3.8.1 | 9092 | 비동기 메시징 (KRaft 단일 노드) |

## 실행 방법
```powershell
# 1) 인프라 기동
docker compose up -d

# 2) 앱 실행 (JDK 17 명시)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat bootRun

# 3) 헬스체크
curl http://localhost:8080/actuator/health
```
