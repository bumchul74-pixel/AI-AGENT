# ADR-008: Agent configuration 관리자 토큰 API와 편집 화면

- 상태: Superseded
- 일자: 2026-08-18
- 관련 결정: ADR-007
- 대체 결정: ADR-009

## 배경

ADR-007은 DB 저장·활성화와 Memory Snapshot 게시 경계를 만들었지만 운영자가 이를 사용할 관리 화면과 인증된 HTTP 경계가 없었다. 프로젝트에는 아직 공통 로그인, SSO 또는 역할 기반 권한 체계가 없다.

## 결정

1. 운영 관리 메뉴에 Agent 설정 관리 화면을 제공한다. 화면은 Agent, capability, dependency, retry, fallback과 maxParallelism을 구조화된 form으로 편집한다.
2. 관리 API는 활성 설정 조회, 새 version 저장·활성화, DB ACTIVE version refresh만 제공한다. 삭제 API와 기존 version 직접 수정은 제공하지 않는다.
3. AGENT_CONFIGURATION_ADMIN_API_ENABLED가 true이고 AGENT_CONFIGURATION_ADMIN_TOKEN이 비어 있지 않을 때만 API를 허용한다. 나머지는 fail-closed 한다.
4. client는 X-Agent-Configuration-Admin-Token header를 사용한다. 비교는 constant-time으로 수행하고 token 값은 로그, DB, 오류 응답에 기록하지 않는다.
5. frontend는 token을 localStorage, sessionStorage, URL 또는 application state persistence에 저장하지 않고 JavaScript module memory에만 유지한다.
6. 저장 요청은 기존 AgentConfigurationService를 사용해 validation, DB transaction, commit 후 Memory publish 순서를 유지한다.

## 결과

- 운영자는 재배포 없이 설정을 DB에 저장하고 같은 instance의 다음 업무부터 즉시 반영할 수 있다.
- 공유 token은 사용자별 식별, 세밀한 권한, 개별 폐기와 감사 추적을 제공하지 않는다.
- SSO/RBAC가 도입되면 header token 인증을 대체하고 관리 API 계약과 서비스 경계는 유지한다.

## 대안

- 무인증 내부망 API는 네트워크 경계 오판 위험 때문에 제외했다.
- 브라우저 저장소 token 보관은 XSS 노출 지속 시간을 늘리므로 제외했다.
- 기존 row를 update하는 방식은 version 추적과 rollback 근거를 잃으므로 제외했다.
