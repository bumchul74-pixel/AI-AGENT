# 연계 시스템 상태 모니터링

## 목표

운영 관리에서 AI-AGENT와 주요 연계 시스템의 최근 상태를 확인하고 명시적으로 즉시 재점검할 수 있게 하며, 대시보드에는 전체 상태 요약을 제공한다.

## 제약

- 상태 화면 조회마다 모든 외부 시스템을 호출하지 않는다.
- 비밀정보, endpoint credential, stack trace를 응답이나 로그에 노출하지 않는다.
- 외부 LLM은 비용과 rate limit 때문에 상태 점검에서 실제 생성 요청을 수행하지 않는다.
- 선택 연계 장애는 전체 서비스 중단과 구분한다.
- 실제 외부 서비스가 없는 deterministic test를 유지한다.

## 비목표

- 운영 SLO, alert threshold, pager 연동을 새로 정의하지 않는다.
- 장애 서비스를 화면에서 시작하거나 재시작하지 않는다.
- RAG, MCP, Neo4j 내부 구현을 Spring Boot에 복제하지 않는다.

## 현재 상태

- 연계 상태를 한곳에서 조회하는 API와 운영 화면이 없다.
- 사용자는 개별 포트와 로그를 직접 확인해야 한다.
- 대시보드는 프로젝트·색인·생성·보안 현황만 제공한다.

## 단계

1. 상태, 중요도, 점검 유형과 집계 규칙을 제품 명세에 기록한다.
2. 외부 경계를 가볍게 점검하는 probe와 캐시된 snapshot 서비스를 구현한다.
3. 캐시 조회 GET과 수동 재점검 POST API를 추가한다.
4. 운영 관리 시스템 상태 화면과 대시보드 요약을 추가한다.
5. 단위·컨트롤러·프런트엔드·전체 gate를 검증한다.

## 검증

- GET은 외부 probe를 실행하지 않고 마지막 snapshot을 반환한다.
- POST만 즉시 probe를 실행하고 snapshot을 갱신한다.
- 핵심 의존성 DOWN은 전체 DOWN, 선택 의존성 DOWN은 DEGRADED로 집계한다.
- probe 예외와 timeout은 안전한 DOWN 상태로 변환한다.
- 로딩, 오류, 미확인, 빈 상태가 화면에 구분된다.
- `verifyAll`

## 위험

- connectivity 점검 성공이 실제 비즈니스 요청 성공을 완전히 보장하지 않는다.
- 너무 짧은 주기는 외부 시스템에 불필요한 부하를 줄 수 있다.
- 애플리케이션 시작 직후에는 초기 snapshot이 UNKNOWN일 수 있다.

## 롤백

system status 패키지, API, 운영 화면, 대시보드 요약, 메뉴·문서 변경을 되돌린다.

## 결정

- 상태는 `UP`, `DEGRADED`, `DOWN`, `UNKNOWN`으로 표현한다.
- RAG는 `/health`, PostgreSQL은 connection validation, MCP와 Neo4j는 endpoint connectivity를 사용한다.
- LLM은 설정 상태만 확인하고 실제 호출 여부를 메시지로 구분한다.
- background 주기 점검 결과를 캐시하고 GET은 캐시만 읽는다.

## 결과

- Backend, PostgreSQL, RAG, AI-MCP, EasyOCR MCP, Neo4j, LLM Provider probe를 구현했다.
- background 점검과 수동 점검 결과를 캐시하며 일반 GET은 캐시만 반환한다.
- 운영 관리 시스템 상태 화면과 대시보드 요약을 추가했다.
- 핵심·선택 의존성에 따른 DOWN·DEGRADED 집계와 안전한 오류 메시지를 적용했다.

## 검증 결과

- `SystemStatusServiceTest`, `SystemStatusControllerTest`: 성공
- `frontendCheck`: 성공
- `.\gradlew.bat verifyAll --console=plain`: 성공 (Java, integration, RAG 16 tests, frontend build, harness docs)
