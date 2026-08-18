# ADR-003: Agent routing과 MCP transport 분리

- 상태: Accepted
- 날짜: 2026-08-14

## 문제

`AiMcpChatContextProvider`가 사용자 요청 분류, Tool 선택, 인자 추출, MCP 실행과 결과 포맷을 모두 담당해 Orchestrator로 확장하기 어렵다.

## 결정

규칙 기반 요청 판단과 실행 대상 선택은 `AgentRouter`가 담당하고, 선택 결과는 transport와 독립적인 `AgentRoute`로 표현한다. `AiMcpChatContextProvider`는 Route를 실행하고 MCP 결과를 대화 컨텍스트로 포맷한다. `AiMcpGatewayService`는 MCP client transport 책임을 유지한다.

## 결과

- Tool 선택 규칙을 외부 호출 없이 단위 테스트할 수 있다.
- 이후 Planner, 정책, 실행 상태 관리가 `AgentRoute`를 중심으로 확장될 수 있다.
- 기존 Tool 선택 우선순위와 외부 API는 유지한다.
- 이번 결정은 다단계 실행이나 LLM 기반 계획을 포함하지 않는다.