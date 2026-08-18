# AgentRouter 분리 실행 계획

## 목표

`AiMcpChatContextProvider`에 결합된 요청 지원 판단, MCP 작업 선택, Tool 인자 추출을 `AgentRouter`로 분리한다.

## 제약

- 기존 MCP Tool 이름, 선택 우선순위, 인자 형식과 오류 메시지를 유지한다.
- `AiMcpGatewayService`는 MCP transport 경계로 유지한다.
- 공개 REST API와 외부 MCP 계약을 변경하지 않는다.
- 실제 MCP 서버 호출 없이 결정적 테스트로 검증한다.

## 비목표

- 다단계 계획, 병렬 실행, 실행 상태 저장은 이번 단계에 포함하지 않는다.
- LLM 기반 동적 라우팅을 도입하지 않는다.
- Agent 실행 결과 통합 정책을 변경하지 않는다.

## 현재 상태

`AiMcpChatContextProvider`가 요청 분류, 인자 파싱, 작업 실행과 컨텍스트 포맷을 모두 담당한다.

## 단계

1. 실행 가능한 작업을 표현하는 `AgentRoute`를 정의한다.
2. 선택과 인자 추출을 담당하는 `AgentRouter`를 구현한다.
3. Provider는 route 실행과 컨텍스트 포맷만 담당하도록 축소한다.
4. AgentRouter 단위 테스트와 기존 Provider 계약 테스트를 실행한다.

## 검증

- `AgentRouterTest`
- `AiMcpChatContextProviderTest`
- `./gradlew.bat verifyAll --console=plain`

## 위험

- 선택 우선순위 변경으로 동일 요청이 다른 Tool로 전달될 수 있다.
- 필수 인자 추출 오류 메시지가 달라질 수 있다.

## Rollback

새 Router와 Route를 제거하고 Provider에 기존 선택 로직을 복원한다. DB와 공개 API 변경이 없어 별도 데이터 rollback은 없다.

## 결정

- Router는 실행하지 않고 실행 계획인 `AgentRoute`만 반환한다.
- Provider가 Gateway와 로컬 프로젝트 분석기를 호출한다.
- 규칙 기반 결정성을 유지하고 LLM Planner는 후속 단계로 둔다.
## 완료 결과

- `AgentRoute`로 실행 종류, operation, target과 arguments를 표준화했다.
- `AgentRouter`로 요청 지원 판단, 작업 선택과 인자 추출을 이동했다.
- `AiMcpChatContextProvider`는 Route 실행과 컨텍스트 포맷만 담당한다.
- Router 단위 테스트를 추가하고 기존 Provider 테스트를 새 주입 경계에 맞췄다.
- 아키텍처, ADR과 MCP 참조 문서를 갱신했다.

## 검증 결과

- 정적 경계 검사: 통과. Provider에 선택·파싱 로직이 없고 Router에 외부 호출 의존성이 없으며 모든 Route 종류가 실행 분기에 대응한다.
- 대상 테스트 첫 시도: 120초 시간 초과, Gradle 출력과 신규 테스트 리포트 없음.
- `--no-daemon` 대상 테스트 재시도: 180초 시간 초과, Gradle/JVM 초기 출력 없음.
- `verifyAll`: 동일한 Gradle 기동 정체 때문에 실행하지 못했다. 코드 실패가 확인된 것은 아니며 실행 환경 정상화 후 재실행이 필요하다.