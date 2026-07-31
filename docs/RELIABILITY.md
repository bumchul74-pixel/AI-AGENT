# 신뢰성 정책

## 장애 경계

- LLM, Python RAG, MCP, PostgreSQL, Neo4j는 독립적으로 실패할 수 있다.
- timeout, 연결 실패, 잘못된 응답은 경계에서 분류한다.
- retry는 멱등성이 보장되는 조회에만 제한한다.
- 생성 성공과 이력 저장 실패 같은 부분 실패를 구분한다.

## 관측성

correlation id, 외부 시스템 종류, 처리 단계, latency, 상태를 구조화해 기록한다. API key, token, 업로드 원문, 전체 prompt/response는 일반 로그에 기록하지 않는다.

## 검증 계층

- `test`: 외부 시스템 없는 Java 검증
- `integrationTest`: Spring wiring과 로컬 의존성
- `ragTest`: Python 검색·인덱싱 단위 동작
- `frontendCheck`: 배포 가능한 frontend bundle
- `verifyAll`: merge 전 deterministic gate
- `liveTest`: 명시적으로 구성된 실제 외부 연동

운영 SLO, error budget, RTO/RPO, alert threshold는 아직 승인되지 않았으며 [기술부채 추적기](exec-plans/tech-debt-tracker.md)에서 관리한다.
