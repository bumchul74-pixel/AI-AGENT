# ADR-012: MCP Tool 무승인 자동 DB 활성화

- 상태: Accepted
- 일자: 2026-08-18

## 결정

MCP tools/list를 기본 30초마다 조회한다. 목록이 변경되면 기존 Agent 정책을 가능한 한 보존하면서 신규 Tool 추가, 삭제 Tool 제거, dependency와 fallback 참조 정리를 수행하고 AgentConfigurationService를 통해 새 immutable DB version을 즉시 활성화한다. 관리자 승인 단계는 두지 않는다.

빈 목록이나 조회·검증·transaction 실패는 동기화 실패로 처리하고 현재 last-known-good Snapshot을 유지한다. 변경이 없으면 version을 생성하지 않는다. 신규 Tool은 auto-discovered-agent에 등록하며 알려진 Tool 이름은 기존 resolver에 연결하고 알 수 없는 Tool은 none resolver로 등록한다.

## 결과

MCP Tool 추가·삭제가 관리 화면과 AI 대화 @ 목록 및 routing에 자동 반영된다. 반면 잘못된 MCP 목록이 자동 정책 변경으로 이어질 수 있으므로 AGENT_CONFIGURATION_TOOL_SYNC_ENABLED=false를 긴급 차단 수단으로 유지한다.