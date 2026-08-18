# ADR-011: DB 기반 AI 대화 Tool catalog

- 상태: Accepted
- 일자: 2026-08-18

## 배경

AI 대화 입력창의 `@` 자동완성은 MCP server의 live `tools/list`를 직접 호출했다. Agent와 Capability의 운영 원본을 DB versioned configuration으로 이전한 뒤에도 화면 목록이 별도 원본을 사용해, 관리 화면에서 활성화·비활성화한 Tool과 대화 목록이 불일치할 수 있었다.

## 결정

1. `GET /api/mcp/agent-tools` read-only endpoint를 추가한다.
2. endpoint는 DB ACTIVE configuration이 게시한 AgentRegistry last-known-good Snapshot의 활성 Capability를 반환한다.
3. 프런트엔드 `@` 자동완성은 신규 endpoint를 사용하고 live `tools/list`를 호출하지 않는다.
4. Tool name과 Agent/Capability 식별자, server, configuration version, resolver 기반 필수 인자 힌트를 반환한다.
5. Tool 호출 routing과 MCP gateway transport는 변경하지 않는다.

## 결과

- 설정 저장·활성화 직후 Tool 목록과 routing이 같은 Memory Snapshot을 사용한다.
- MCP server 연결 장애가 catalog 원본을 바꾸지는 않는다.
- catalog에 Tool이 존재한다는 사실은 MCP server의 live 연결 또는 schema 일치를 보장하지 않는다.

## 대안

- 요청마다 DB를 직접 조회하면 DB 부하와 Snapshot 불일치가 발생할 수 있어 제외했다.
- DB 목록과 live `tools/list`를 교집합으로 반환하면 연결 장애 시 설정 목록이 사라지고 원본이 다시 이원화되어 제외했다.