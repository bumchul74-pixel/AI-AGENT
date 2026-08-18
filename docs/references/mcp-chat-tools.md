# AI 질의에서 MCP tool 실행

AI 질의 화면에서 AI-MCP tool 이름을 명시하면 해당 이름을 `tools/list` 조회로 처리하지 않고 `tools/call`로 실행한다.

## 대화 입력 Tool catalog

- `GET /api/mcp/agent-tools`는 DB ACTIVE Agent configuration이 게시한 현재 AgentRegistry Snapshot을 조회한다.
- 활성 Agent의 활성 Capability만 반환하며 Snapshot priority 순서를 유지한다.
- 응답에는 configurationVersion, tool name, Agent/Capability ID, server, resolver 기반 필수 인자 힌트를 포함한다.
- 프런트엔드의 `@` Auto Complete는 이 endpoint만 사용하고 MCP `tools/list`를 직접 호출하지 않는다.
- 저장·활성화 transaction 이후 Memory Snapshot이 교체되면 다음 catalog 조회에 즉시 반영된다.
- catalog는 설정 상태이며 MCP server의 live 연결 또는 schema 일치 여부를 보장하지 않는다.

## 호출 형식

필수 인자가 없는 tool은 이름만 명시할 수 있다.

```text
list_rules를 실행해서 보안 규칙 목록을 보여줘
list_database_tables를 실행해서 전체 테이블을 보여줘
```

필수 인자가 있는 tool은 `key=value` 또는 `key="value"` 형식으로 전달한다.

```text
describe_database_table_columns tableName=rag_document
search_database_tables keyword=document schemaName=public
scan_file path=src/main/java/example/Sample.java
analyze_project_structure projectPath=D:\workspace\sample
```

값에 공백이 있으면 따옴표로 감싼다. `scan_source`는 `fileName`과 `source`가 모두 필요하며 source 대신 Java, SQL 또는 XML 코드 블록을 사용할 수 있다.

필수 인자가 없으면 다른 MCP 작업으로 폴백하지 않고 어떤 인자가 필요한지 오류로 반환한다.

## 지원 tool

- OCR: `ocr_image_base64`, `ocr_image_file`, `ocr_document_base64`
- Secure coding: `scan_file`, `scan_project`, `scan_source`, `list_rules`
- Database: `list_database_tables`, `search_database_tables`, `describe_database_table_columns`, `describe_database_foreign_keys`, `describe_database_indexes`, `describe_database_comments`, `generate_mybatis_mapper`
- Project/source: `analyze_project_structure`, `search_source_ontology`
- Server: `get_server_info`

화면에 표시하는 MCP 참조명은 결과 본문에 포함된 다른 tool 이름이 아니라 실제 `MCP gateway operation` 값을 사용한다.

## 내부 Routing 경계

- `AgentRegistry`는 `DB ACTIVE version의 Memory Snapshot` 설정에서 활성 Agent와 capability를 등록하고 id, Tool과 priority를 검증한다.
- `AgentRouter`는 Registry에서 capability를 선택하고 Tool, resource, prompt 또는 로컬 프로젝트 분석 작업을 `AgentRoute`로 만든다.
- `AgentArgumentResolver`는 capability의 `argument-resolver` 이름에 따라 Tool 인자를 검증하고 추출한다.
- `AgentRouter`와 Resolver는 MCP 서버를 직접 호출하지 않는다.
- AgentOrchestrator는 요청당 하나의 Route를 실행하고 성공·실패 이력을 기록한다.
- 실행 이력에는 Agent/capability, route kind, 안전한 target, 요청 hash, 상태와 소요시간만 저장한다.
- AiMcpChatContextProvider는 Orchestrator 실행 결과를 대화 컨텍스트로 변환한다.
- `AiMcpGatewayService`는 AI-MCP client 선택, 연결 초기화와 MCP transport만 담당한다.
