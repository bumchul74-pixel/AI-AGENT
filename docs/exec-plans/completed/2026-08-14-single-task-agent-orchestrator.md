# 단일 작업 AgentOrchestrator와 실행 이력

## 목표

한 사용자 요청에서 하나의 `AgentRoute`를 선택하고 동기 실행하는 `AgentOrchestrator`를 추가한다. 선택된 Agent/capability, 상태, 소요시간을 PostgreSQL 실행 이력으로 남긴다.

## 제약

- `AgentRouter`는 선택과 인자 해석만 담당하고 외부 시스템을 호출하지 않는다.
- `AiMcpChatContextProvider`는 orchestration 결과를 대화 컨텍스트로 변환한다.
- 요청 원문, Tool 인자/결과, 로컬 경로, credential은 실행 이력과 로그에 저장하지 않는다.
- 실행 이력 저장 실패가 Agent 실행 자체를 차단하지 않도록 부분 실패를 구분한다.

## 비목표

- 다중 Agent 계획, 병렬 실행, 작업 큐, retry, 보상 트랜잭션
- 실행 이력 조회 API와 Frontend 화면
- 실제 외부 MCP 호출을 포함한 `liveTest`

## 현재 상태

`AgentRouter`가 단일 route를 반환하고 `AiMcpChatContextProvider`가 직접 route를 실행한다. Agent 실행 전후 상태를 저장하는 경계와 테이블은 없다.

## 단계

1. 실행 이력 schema, domain, repository, MyBatis mapper를 추가한다.
2. route 선택, 실행, 성공/실패 기록을 조정하는 `AgentOrchestrator`를 추가한다.
3. `AiMcpChatContextProvider`가 orchestrator를 사용하도록 변경한다.
4. 성공, 실행 실패, 이력 저장 실패, 단일 route 실행을 단위 테스트한다.
5. 문서와 기본 gate를 검증하고 계획을 completed로 이동한다.

## 검증

- Agent orchestration 관련 Java 단위 테스트
- `./gradlew.bat test`
- `./gradlew.bat verifyAll --console=plain`

## 위험

- DB 장애가 MCP 실행을 연쇄 실패시킬 수 있다.
- 오류 메시지나 route target을 그대로 저장하면 민감한 요청 정보가 노출될 수 있다.
- 기존 Provider 테스트가 orchestration 경계 변경으로 과도하게 결합될 수 있다.

## Rollback

Provider를 기존 `AgentRouter` 직접 실행 구조로 되돌리고 새 Java/MyBatis 파일을 제거한다. `agent_execution_history` 테이블은 읽는 코드가 없으므로 잔존해도 기존 동작에 영향을 주지 않는다.

## 결정

- 한 번의 `execute` 호출은 정확히 하나의 route만 실행한다.
- 실행 이력은 UUID, Agent/capability, route kind, 안전한 target, 요청 SHA-256, 상태, 시간, 오류 유형만 저장한다.
- 이력 저장 실패는 warning으로 분리하고 원래 Agent 실행 결과를 유지한다.

## 완료 결과

- `AgentOrchestrator`가 요청당 하나의 Route를 선택·실행하도록 Provider 실행 책임을 이동했다.
- `agent_execution_history` schema와 MyBatis Repository를 추가해 STARTED/SUCCEEDED/FAILED, Agent/capability와 소요시간을 저장한다.
- 요청 원문, 인자, 결과, 로컬 경로 대신 SHA-256과 안전한 target만 저장한다.
- 이력 저장 실패가 Agent 실행을 차단하지 않는 단위 테스트를 추가했다.

## 검증 결과

- Agent orchestration 관련 Java 테스트: 성공
- Mapper XML 파싱: 성공
- `validateHarnessDocs`: 성공
- `verifyAll --console=plain`: 성공
- Frontend production build: 성공, 기존 500 kB chunk 경고만 남음
