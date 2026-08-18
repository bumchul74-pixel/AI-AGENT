# ADR-005: 단일 작업 Agent orchestration과 실행 이력

- 상태: Accepted
- 날짜: 2026-08-14

## 문제

`AgentRouter`가 선택한 Route를 `AiMcpChatContextProvider`가 직접 실행해 실행 상태와 소요시간을 일관되게 추적할 경계가 없다. 다중 Agent 계획에 앞서 한 요청을 하나의 Agent 작업으로 실행하는 최소 orchestration 경계가 필요하다.

## 결정

`AgentOrchestrator`가 한 요청에서 하나의 `AgentRoute`를 선택하고 동기 실행한다. 성공 또는 실패 결과는 `agent_execution_history`에 기록한다. 이력 저장은 Repository/MyBatis 경계를 사용하며 저장 장애는 Agent 실행과 분리한다.

이력에는 execution UUID, Agent/capability, route kind, 안전한 target, 요청 SHA-256, 상태, 소요시간과 오류 유형만 저장한다. 요청 원문, Tool 인자/결과, 로컬 경로와 외부 오류 원문은 저장하지 않는다.

`AiMcpChatContextProvider`는 Orchestrator 실행 결과를 대화 컨텍스트로 변환한다. `AgentRouter`는 외부 호출 없이 선택과 인자 해석만 유지한다.

## 결과

- 요청당 하나의 Agent 작업만 실행하는 경계가 생긴다.
- 성공, 실패와 실행 시간을 DB에서 추적할 수 있다.
- 이력 DB 장애가 MCP 또는 로컬 분석 실행 결과를 덮어쓰지 않는다.
- 다중 Agent 계획, 병렬 실행, retry, queue와 이력 조회 API는 후속 단계로 남는다.
