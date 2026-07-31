# AI-AGENT 아키텍처

## 목적

AI-AGENT는 자연어 요청을 표준 문서와 표준 소스코드에 근거한 Java 코드로 변환한다. 검색 근거, 생성 결과, 이력을 연결하면서 LLM Provider와 검색·저장 기술을 교체할 수 있어야 한다.

## 시스템 컨텍스트

```text
개발자 -> React/Vite -> Spring Boot REST API
                         -> Python RAG -> Embedding/Vector Store
                         -> LLM Client Strategy -> OpenAI/Gemini
                         -> MCP Router/Gateway -> MCP servers
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
| `mcp` | routing과 gateway | feature별 transport 중복 |
| `sourcegraph` | 분석과 graph port | Neo4j 타입 누출 |

새 Provider에는 구현, 설정, factory 선택, contract test를 추가한다. 공통 경계나 의존 방향을 바꾸면 실행 계획과 ADR이 필요하다. `verifyAll`은 기본 gate이며 실제 외부 시스템 검증은 `liveTest`로 분리한다.
