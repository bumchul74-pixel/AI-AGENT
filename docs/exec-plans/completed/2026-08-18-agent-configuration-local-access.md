# Agent configuration 로컬 활성화와 저장·삭제 UX

## 목표

Agent 설정 관리 화면이 localhost에서 ACTIVE DB 설정을 조회하고, Agent와 Capability를 추가·편집·삭제한 뒤 새 version으로 저장·활성화할 수 있게 한다.

## 제약

- 로그인과 token이 없는 동안 원격 관리 요청은 허용하지 않는다.
- 기존 version은 실행 이력 추적을 위해 물리 삭제하지 않는다.
- validation, DB transaction, commit 이후 Memory Snapshot publish 순서를 유지한다.
- API 오류는 전역 Toast로 표시한다.

## 비목표

- 로그인, 사용자, 역할, 감사 UI 구현
- 기존 configuration version 물리 삭제 API 추가

## 현재 상태

- token 인증은 제거되었지만 admin-api-enabled 기본값이 false여서 최초 GET이 503을 반환한다.
- 최초 조회 실패로 configuration이 null이며 편집·삭제 영역이 렌더링되지 않는다.

## 단계

1. 관리 API를 localhost에서 기본 사용 가능하게 하고 원격 요청 차단과 false override를 유지한다.
2. 조회 실패 재시도와 명확한 저장·삭제 안내 및 버튼을 추가한다.
3. 기본값, 명시적 차단, 원격 차단 동작을 테스트한다.
4. 아키텍처, 제품, 보안, 설정, ADR 문서를 갱신한다.
5. targeted test, frontendCheck, verifyAll을 실행한다.

## 검증

- 기본 properties에서 로컬 관리 요청 허용
- flag=false에서 503 및 service 미호출
- 원격 request address에서 403
- frontend production build
- verifyAll

## 위험

- localhost reverse proxy를 통하면 remote address 기반 제한이 외부 요청을 구분하지 못할 수 있다.
- 외부 배포에서는 AGENT_CONFIGURATION_ADMIN_API_ENABLED=false가 필요하다.

## Rollback

AGENT_CONFIGURATION_ADMIN_API_ENABLED=false로 API를 즉시 차단하고 기본값을 false로 되돌린다.

## 결정

- 화면 중심 관리 요구를 위해 localhost 범위에서만 기본 활성화한다.
- 삭제는 새 ACTIVE version에서 항목을 제외하는 논리적 삭제로 정의한다.
## 완료 증거

- AgentConfigurationAdminAccessGuardTest, AgentOrchestrationPropertiesTest, AgentConfigurationAdminControllerTest: BUILD SUCCESSFUL
- frontendCheck: Vite production build 성공
- verifyAll 1차: BUILD SUCCESSFUL (1m 8s)
- verifyAll 재실행: BUILD SUCCESSFUL (27s)
- Java test/integrationTest, Python RAG 16 tests, frontendCheck, validateHarnessDocs, doctor 통과
- Vite 500 kB chunk 경고는 기존 비차단 경고로 유지