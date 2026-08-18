# MCP Tool 무승인 자동 동기화

## 목표

MCP tools/list 변경을 주기적으로 감지하고 관리자 승인 없이 DB 새 version으로 저장·활성화한다.

## 제약

- 기존 configuration validation, transaction, commit 이후 Memory publish를 재사용한다.
- 조회·저장 실패와 빈 tools/list는 현재 Snapshot을 유지한다.
- 변경이 없으면 새 DB version을 생성하지 않는다.

## 동작

- 기본 30초 주기로 동기화한다.
- 기존 Tool 정책은 보존한다.
- 사라진 Tool은 제거하고 dependency/fallback 참조를 정리한다.
- 신규 Tool은 auto-discovered-agent에 추가한다.
- 알려진 Tool은 기존 resolver를 자동 지정하고 알 수 없는 Tool은 none resolver를 사용한다.

## 검증

- 추가·삭제·참조 정리와 무변경 테스트
- verifyAll

## 위험

- MCP 서버의 일시적인 불완전 목록도 DB 변경으로 이어질 수 있다.
- 알 수 없는 신규 Tool은 인자 없는 호출 정책으로 등록된다.

## Rollback

AGENT_CONFIGURATION_TOOL_SYNC_ENABLED=false로 자동 동기화를 중단하고 이전 DB version 내용을 관리 화면에서 새 version으로 복구한다.
## 완료 증거

- McpToolConfigurationSynchronizerTest와 properties test: BUILD SUCCESSFUL
- verifyAll: BUILD SUCCESSFUL (1m 42s)
- Java test/integrationTest, Python RAG 16 tests, frontendCheck, 문서 검증 통과