# AI-AGENT 아키텍처

## 목적

AI-AGENT는 자연어 요청을 표준 문서와 표준 소스코드에 근거한 Java 코드로 변환한다. 검색 근거, 생성 결과, 이력을 연결하면서 LLM Provider와 검색·저장 기술을 교체할 수 있어야 한다.

## 시스템 컨텍스트

```text
개발자 -> React/Vite -> Spring Boot REST API
                         -> Python RAG -> Embedding/Vector Store
                         -> LLM Client Strategy -> OpenAI/Gemini
                         -> Agent Router -> Agent Orchestrator -> MCP Gateway -> MCP servers
                         -> MyBatis -> PostgreSQL
                         -> Source Graph Port -> Neo4j/No-op
```

## 핵심 흐름

```text
생성 요청 -> 요청 검증/프로젝트 분석 -> RAG/MCP 근거 검색
          -> 근거 기반 prompt -> 설정된 LLM Provider
          -> 코드와 근거 반환 -> 생성 이력 저장
```

검색 실패를 근거 없는 임의 생성으로 숨기지 않는다.

## 의존성 규칙

- `controller -> service -> repository/mapper` 방향을 유지한다.
- Domain과 DTO는 외부 LLM, RAG, MCP, Neo4j 구현 타입에 의존하지 않는다.
- `llm`, `rag`, `mcp`, `sourcegraph`가 외부 시스템 경계를 소유한다.
- Python RAG 내부 구현을 Spring Boot에서 재구현하지 않는다.
- React는 화면 상태와 API 표현만 담당한다.

## 패키지 책임

| 영역 | 책임 | 금지 사항 |
| --- | --- | --- |
| `chat` | 대화와 이력 | LLM/RAG 구현 중복 |
| `generation` | 근거 기반 코드 생성 | 근거 없는 표준 생성 |
| `document` | 업로드·저장·인덱싱 workflow | RAG 저장소 직접 접근 |
| `rag` | REST 계약과 hybrid 검색 | Python vector 구현 복제 |
| `llm` | Provider 전략과 오류 번역 | API key 하드코딩 |
| `mcp` | Agent routing, 단일 작업 orchestration과 MCP gateway | routing에서 외부 호출, feature별 transport 중복 |
| `sourcegraph` | 분석과 graph port | Neo4j 타입 누출 |

새 Provider에는 구현, 설정, factory 선택, contract test를 추가한다. 공통 경계나 의존 방향을 바꾸면 실행 계획과 ADR이 필요하다. `verifyAll`은 기본 gate이며 실제 외부 시스템 검증은 `liveTest`로 분리한다.

## Agent workflow orchestration

AgentPlanner는 명시된 MCP tool과 capability dependency로 결정적인 DAG를 구성한다. AgentOrchestrator는 준비된 단계를 제한된 병렬도로 실행하고 capability별 retry/fallback 정책을 적용한다. 채팅 API에는 원문 실행 데이터가 아닌 안전한 단계 상태 요약만 전달한다. 상세 결정은 [ADR-006](docs/design-docs/decisions/ADR-006-agent-workflow-orchestration.md)을 따른다.

Agent orchestration 정책의 원본은 PostgreSQL의 versioned configuration이다. 저장·활성화 transaction이 commit된 뒤 검증된 불변 Snapshot을 Memory에 게시하며, 업무 요청은 DB가 아니라 시작 시 캡처한 Snapshot만 참조한다. 외부 변경은 polling으로 반영하고 실패 시 last-known-good Snapshot을 유지한다. 빈 DB 초기화와 장애 복구에는 bundled bootstrap JSON을 사용하며 application.yml에는 이 동작을 제어하는 최소 운영 flag만 둔다. 상세 결정은 [ADR-007](docs/design-docs/decisions/ADR-007-database-agent-configuration.md)을 따른다.
AI 대화의 `@` Tool catalog도 같은 AgentRegistry Snapshot에서 활성 Capability를 조회한다. 따라서 화면 목록과 실제 routing은 동일한 configuration version을 기준으로 하며, catalog 조회가 MCP server의 live `tools/list`에 의존하지 않는다. 상세 결정은 [ADR-011](docs/design-docs/decisions/ADR-011-db-backed-chat-tool-catalog.md)을 따른다.

운영 관리의 Agent 설정 관리 화면은 로컬 요청에 한해 기본 활성화된 관리 REST API를 통해 활성 Snapshot을 조회하고 새 immutable version을 저장·활성화한다. 환경변수로 API 전체를 차단할 수 있고 원격 요청은 403으로 거부한다. Controller는 접근 gate와 DTO mapping만 담당하고 transaction과 Memory publish는 AgentConfigurationService에 위임한다. 상세 결정은 [ADR-010](docs/design-docs/decisions/ADR-010-agent-configuration-local-access.md)을 따른다.

## Neo4j data explorer

The `neo4jexplorer` package owns generic, read-only Neo4j node browsing. It is independent from the domain-specific `sourcegraph` projection. HTTP mapping stays in the controller, page normalization and use-case rules stay in the service, and Cypher remains behind the repository boundary. See [ADR-013](docs/design-docs/decisions/ADR-013-neo4j-data-explorer.md).
