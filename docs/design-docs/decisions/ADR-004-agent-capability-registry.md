# ADR-004: 설정 기반 Agent capability Registry

- 상태: Accepted
- 날짜: 2026-08-14

## 문제

Agent Tool 목록과 인자 추출 전략이 `AgentRouter`에 하드코딩되어 Agent 추가·비활성화와 routing 정책 변경마다 Router를 수정해야 한다.

## 결정

Agent와 capability의 식별자, MCP Tool, intent, 인자 Resolver, 우선순위, timeout과 승인 정책을 `agent.orchestration` 설정으로 관리한다. `AgentRegistry`는 활성 설정을 검증하고 불변 capability 목록으로 제공한다. `AgentRouter`는 Registry에서 capability를 선택하며 인자 추출은 이름으로 선택되는 `AgentArgumentResolver`가 담당한다.

MCP Tool의 실제 입력 schema는 `tools/list`를 기준으로 유지하고 설정 파일에 중복하지 않는다.

## 결과

- Agent 활성화와 routing 우선순위를 설정으로 관리할 수 있다.
- 중복 capability id와 Tool은 시작 시 실패한다.
- 새 인자 처리 방식은 Resolver 구현으로 확장할 수 있다.
- 다단계 계획과 실행 상태 관리는 후속 Orchestrator 단계로 남는다.