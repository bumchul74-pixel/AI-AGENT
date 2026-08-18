# DB 기반 AI 대화 Tool catalog

## 목표

AI 대화 입력창의 `@` 자동완성 Tool 목록을 MCP `tools/list`가 아니라 DB ACTIVE Agent configuration에서 게시된 Memory Snapshot으로 제공한다.

## 제약

- 업무 요청은 DB를 직접 반복 조회하지 않고 불변 AgentRegistry Snapshot을 참조한다.
- 활성 Agent와 활성 Capability의 Tool만 노출한다.
- configuration 저장·활성화 직후 목록 변경이 반영되어야 한다.
- 목록 실패가 일반 채팅 입력을 차단하지 않아야 한다.

## 비목표

- MCP 서버의 실제 연결 상태 또는 live schema 검증
- Tool 호출 routing 변경
- configuration DB schema migration

## 현재 상태

- 프런트엔드는 `GET /api/mcp/ai-mcp/tools`를 호출한다.
- 해당 endpoint는 MCP 서버의 live `tools/list` 결과를 반환한다.

## 단계

1. AgentRegistry Snapshot 기반 read-only Tool catalog service와 API를 추가한다.
2. resolver별 필수 인자 힌트를 catalog 응답에 포함한다.
3. 프런트엔드 `@` 목록 API를 DB 기반 catalog endpoint로 교체한다.
4. 서비스·Controller 테스트와 제품·참조 문서를 갱신한다.
5. frontendCheck와 verifyAll을 실행한다.

## 검증

- 활성 Snapshot의 Tool만 우선순위 순서로 반환
- configuration version, Agent/Capability 식별자, 필수 인자 반환
- 프런트엔드 production build
- verifyAll

## 위험

- catalog는 설정 상태를 나타내며 MCP 서버의 실시간 연결 가능성을 보장하지 않는다.

## Rollback

프런트엔드 조회 경로를 기존 `/api/mcp/ai-mcp/tools`로 되돌리고 신규 read-only endpoint를 제거한다.

## 결정

- DB ACTIVE configuration을 원본으로 하되 요청마다 DB를 조회하지 않고 AgentRegistry의 last-known-good Snapshot을 사용한다.

## 완료 증거

- AgentToolCatalogServiceTest, AgentToolCatalogControllerTest: BUILD SUCCESSFUL
- frontendCheck: Vite production build 성공
- verifyAll: BUILD SUCCESSFUL (1m 30s)
- Java test/integrationTest, Python RAG 16 tests, frontendCheck, validateHarnessDocs, doctor 통과
- Vite 500 kB chunk 경고는 기존 비차단 경고로 유지