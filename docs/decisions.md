# 설계 결정 기록 (Decision Log)

> 면접에서 "왜 이렇게 했나"에 답하기 위한 근거 기록.
> 형식: 결정 / 맥락 / 대안 / 선택 이유 / 트레이드오프.

---

## 0. 이 프로젝트가 실증하려는 이력서 경험

이력서의 다음 경험을 **직접 구현하고 측정**해서 면접에서 설명 가능하게 만드는 것이 목표다.

| 이력서 항목 | 출처(이력서) | 이 프로젝트의 실증 축 |
|---|---|---|
| Kafka 비동기 주문 처리, DLQ + 재시도, 1000 TPS 대응, 응답시간 50% 단축 | 폐쇄몰 주문 시스템(2024.05~12) | **축 1. 주문 처리** — 동기 vs Kafka 비동기 비교 측정 |
| 캐시 계층화 (CDN → Redis L2 → Caffeine L1 → MySQL), 변경 시 무효화 | 폐쇄몰 주문 시스템 캐시 플로우 | **축 2. 상품 조회 캐시 계층화** |
| Spring Batch 월별 정산, Spring Security RBAC, 특정 IP 접근 제어 | 백오피스 Java 마이그레이션(2024.12~2025.03) | **축 3. 백오피스 (RBAC + Batch)** |
| 1000 TPS 감당 웹사이트 운영 | Skills 요약 | **축 4. 부하테스트 (k6, 1000 TPS 목표)** |

핵심: 이력서엔 "성과 수치"(매출 300%, 다운타임 80% 감소 등)만 적혀 있고 **재현 가능한 측정 근거가 없다**.
이 프로젝트는 그 수치를 **내 손으로 재현·측정**해서, 면접에서 "어떻게 측정했고 병목이 무엇이었나"까지 말할 수 있게 한다.

---

## 1. 빌드 도구: Gradle (Groovy DSL)

- **맥락**: 빈 폴더에서 시작, 빌드 도구 미정.
- **대안**: Maven(pom.xml), Gradle Kotlin DSL.
- **선택**: Gradle Groovy DSL.
- **이유**: Spring Boot 포트폴리오에서 가장 일반적, 빌드 속도·설정 간결, wrapper로 별도 설치 불필요(팀/면접관 재현 용이). Groovy 예제가 Kotlin DSL보다 풍부.
- **트레이드오프**: Kotlin DSL 대비 타입 안정성·IDE 자동완성이 약함. 데모 규모에선 영향 작음.

## 2. 아키텍처: 모듈러 모놀리식 (단일 Spring Boot 앱)

- **맥락**: 이력서엔 Payment / Order / Notification 이 **별도 서비스(MSA)**로 기술됨.
- **대안**: Gradle 멀티모듈, 완전 MSA(여러 부트 앱).
- **선택**: 단일 앱 + 도메인별 패키지(`order`, `product`, `backoffice`, `common`). Kafka는 토픽으로 논리 분리.
- **이유**: 데모의 목적은 "기술 포인트 실증"이지 운영 가능한 MSA가 아니다. 단일 앱이면 실행·부하측정·디버깅이 단순하고, Kafka 비동기/캐시/RBAC/Batch 4축을 모두 한 프로세스에서 보여줄 수 있다. 프로젝트 원칙 "범위를 넓히지 말 것"과 일치.
- **트레이드오프**: 진짜 서비스 경계(네트워크 분리, 독립 배포)는 보여주지 못함. → 면접에선 "왜 데모는 모놀리식으로 단순화했고, 실제 MSA였다면 무엇이 달라지는가"를 설명하는 방식으로 보완.

## 3. 런타임: Java 17 + Spring Boot 3.5.14

- **맥락**: 시스템에 JDK 17(Corretto/Oracle)과 JDK 11이 공존. `JAVA_HOME`은 11을 가리킴.
- **결정**: Java 17 toolchain을 `build.gradle`에 고정(`JavaLanguageVersion.of(17)`). 빌드 시 `JAVA_HOME=C:\Program Files\Java\jdk-17` 사용.
- **Spring Boot 버전**: start.spring.io 기본값이 4.0.6으로 올라가 있었으나, **3.5.14(최신 3.x 안정판)로 다운고정**.
- **이유**: (1) 프로젝트 원칙가 "Spring Boot 3.x" 명시. (2) 이력서 경험이 3.x 기반. (3) 4.0은 출시 직후라 레퍼런스·예제가 적고 호환 이슈 위험. 포트폴리오는 안정·재현성이 우선.
- **트레이드오프**: 4.0 신기능 미사용. 데모 목적상 불필요.

## 4. 로컬 인프라: Docker Compose (MySQL · Redis · Kafka)

- **MySQL 8.0**: 영속 저장소. 호스트 **3307** 포트로 매핑(로컬 mysqld가 3306 점유 → 충돌 회피).
- **Redis 7**: L2 캐시 + 세션. `appendonly yes`(AOF) — 이력서의 "AOF로 세션 영구 저장" 경험 반영.
  - 호스트 포트 **16379** 매핑: 기본 6379가 Windows Hyper-V 동적 예약 포트 범위(6290–6389)에 걸려 바인딩 실패(`netsh interface ipv4 show excludedportrange protocol=tcp`로 확인). 이 범위는 재부팅마다 바뀔 수 있어 `winnat` 재시작 같은 일시 조치 대신 범위 밖 포트로 고정.
- **Kafka 3.8.1 (KRaft 모드, 단일 노드)**:
  - **대안**: Zookeeper 동반 구성, Confluent 이미지.
  - **선택**: KRaft 단일 노드(apache/kafka 공식 이미지).
  - **이유**: Zookeeper 제거로 컨테이너 1개로 Kafka 운영 → 데모 환경 단순화. 공식 이미지가 KRaft 스토리지 자동 포맷 지원.
  - **트레이드오프**: 단일 브로커라 복제·고가용성은 시연 불가(replication-factor=1). 데모 목적상 수용.

## 앞으로 채울 결정들 (TODO)
- [ ] 주문 멱등성 보장 방식 (주문ID 기준 중복 방지 — DB 유니크 제약 vs Redis SETNX vs Kafka 키 기반)
- [ ] 캐시 무효화 전략 (write-through vs invalidate-on-write, L1/L2 정합성, Cache Stampede 대응)
- [ ] DLQ + 재시도 정책 (재시도 횟수, 백오프, DLQ 라우팅)
- [ ] RBAC 권한 모델 (역할/권한 테이블 설계, 메서드 보안 vs URL 보안)
- [ ] 정산 배치 청크 크기 / 트랜잭션 경계
- [ ] 1000 TPS 측정 시 커넥션 풀·스레드 풀·Kafka 파티션 수 튜닝
