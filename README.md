# FORME Operations Hub

F&F 디지털본부 Java/Spring ERP·AI 시스템 직무를 목표로 설계한 글로벌 패션 주문·재고 통합 운영 플랫폼입니다. 고객용 이커머스인 [FORME](https://github.com/khp9798/forme-fashion-commerce)는 그대로 유지하고, 이 프로젝트는 사내 직원이 여러 브랜드·채널·창고의 업무를 처리하는 독립 시스템으로 개발합니다.

## 목표

- Spring Boot 기반 REST API와 React·TypeScript 운영 화면
- 창고·매장·SKU별 재고 및 모든 이동 원장
- 외부 OMS·WMS·ERP 데이터를 받는 대량 배치와 실패 재처리
- 역할별 권한, 승인 흐름과 관리자 감사 로그
- 업무 매뉴얼 RAG와 안전한 AI 조회 도구
- PostgreSQL·Docker·CI/CD·모니터링을 포함한 운영 가능한 구조

## 아키텍처 원칙

처음부터 마이크로서비스로 나누지 않고 **모듈형 모놀리스**로 시작합니다. 주문 연동, 재고, 배치, 감사처럼 업무 경계는 코드에서 분리하지만 하나의 애플리케이션과 트랜잭션으로 개발 복잡도를 낮춥니다. 규모와 조직이 커져 독립 배포가 필요한 모듈만 나중에 분리할 수 있습니다.

```mermaid
flowchart LR
  OMS["External OMS"] --> API["Spring Boot integration API"]
  WMS["External WMS"] --> API
  CSV["CSV / Excel upload"] --> BATCH["Spring Batch"]
  API --> CORE["Inventory · Orders · Audit"]
  BATCH --> CORE
  CORE --> DB["PostgreSQL"]
  UI["React operations console"] --> API
  AI["AI operations assistant"] --> API
```

## 현재 구현

- Java 21, Spring Boot 4.1, Gradle
- React 19, TypeScript, Vite
- PostgreSQL 17 개발 컨테이너
- Flyway 초기 스키마: 창고, 상품, SKU, 재고 포지션, 재고 원장, 연동 작업
- Actuator와 시스템 정보 REST API
- 운영 대시보드 첫 화면
- 백엔드·프론트엔드 GitHub Actions 검증
- 창고·상품·SKU 검색 및 창고별 가용 재고 조회 API
- 입고·조정·예약·해제·파손 재고 이동과 변경 원장
- 행 잠금과 멱등성 키를 이용한 동시 요청·중복 요청 보호

## 로컬 실행

```bash
cp .env.example .env
docker compose up -d postgres

cd backend
JAVA_HOME="$(brew --prefix openjdk@21)" ./gradlew bootRun

cd ../frontend
npm install
npm run dev
```

- API: `http://localhost:8080/api/v1/system/info`
- Health: `http://localhost:8080/actuator/health`
- Web: `http://localhost:5173`

개발용 운영 계정의 기본값은 `ops-admin` / `forme-local-admin`입니다. 실제 환경에서는 반드시 `OPS_USERNAME`, `OPS_PASSWORD` 환경변수로 교체합니다. PostgreSQL은 Mac에 설치된 DB와 충돌하지 않도록 기본적으로 `5433` 포트를 사용합니다.

## 재고 API

```bash
# 재고 검색
curl -u ops-admin:forme-local-admin \
  'http://localhost:8080/api/v1/inventory?warehouseCode=ICN-01&query=MLB'

# 재고 5개 예약
curl -u ops-admin:forme-local-admin \
  -H 'Content-Type: application/json' \
  -d '{
    "warehouseCode":"ICN-01",
    "skuCode":"MLB-CAP-0091-BK-F",
    "movementType":"RESERVE",
    "quantity":5,
    "idempotencyKey":"order-1001-reserve",
    "reason":"출고 예약"
  }' \
  http://localhost:8080/api/v1/inventory/movements
```

`idempotencyKey`가 같은 요청을 다시 보내면 재고를 또 차감하지 않고 최초 처리 결과를 돌려줍니다. 재고 행은 변경하는 동안 잠가 동시에 여러 요청이 들어와도 수량 검증과 변경이 한 줄로 처리됩니다.

## 다음 구현 순서

1. 재고 운영 화면과 권한별 로그인
2. 외부 주문 CSV 업로드 및 검증
3. Spring Batch 청크 처리·실패 격리·재시작
4. 역할 기반 승인·감사 로그
5. 판매·재고 집계 및 SQL 튜닝
6. RAG 기반 업무 매뉴얼과 AI 조회 도구
