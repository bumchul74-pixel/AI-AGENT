# Agent capability Registry 실행 계획

## 목표

Agent와 capability 메타데이터를 `application.yml`에 등록하고 `AgentRouter`가 설정 기반 `AgentRegistry`를 사용하도록 변경한다.

## 제약

- 기존 MCP Tool 이름, 선택 우선순위, 인자 형식과 오류 메시지를 유지한다.
- MCP `tools/list`가 Tool 입력 스키마의 기준이며 설정에는 routing·정책 metadata만 둔다.
- credential과 실제 endpoint를 capability 설정에 포함하지 않는다.
- 공개 REST API를 변경하지 않는다.

## 비목표

- LLM Planner, 다단계 실행과 실행 상태 저장은 포함하지 않는다.
- MCP Tool schema를 application.yml에 복제하지 않는다.
- 승인 UI와 동적 설정 편집 화면은 포함하지 않는다.

## 현재 상태

`AgentRouter`가 지원 Tool 목록과 Tool별 인자 추출 분기를 Java 코드에 직접 보유한다.

## 단계

1. `AgentOrchestrationProperties`, `AgentCapability`, `AgentRegistry`를 추가한다.
2. Agent별 capability를 `application.yml`에 등록한다.
3. 인자 추출을 이름 기반 `AgentArgumentResolver`로 분리한다.
4. `AgentRouter`를 Registry와 Resolver 기반으로 전환한다.
5. 설정 바인딩, 중복 검증과 기존 routing 계약을 검증한다.

## 검증

- `AgentRegistryTest`
- `AgentRouterTest`
- `AiMcpChatContextProviderTest`
- `./gradlew.bat verifyAll --console=plain`

## 위험

- 설정 누락이나 중복 Tool 등록 시 애플리케이션 시작이 실패할 수 있다.
- priority 변경 시 명시적 Tool이 여러 개 포함된 요청의 선택 결과가 바뀔 수 있다.

## Rollback

Capability 설정과 Registry·Resolver를 제거하고 `AgentRouter`의 기존 상수·분기로 복원한다. 데이터 migration은 없다.

## 결정

- Registry는 시작 시 활성 Agent와 capability만 불변 목록으로 구성한다.
- Tool schema가 아닌 capability id, tool, intent, resolver, priority와 실행 정책만 설정한다.
- 필수 설정 오류는 조용히 폴백하지 않고 시작 단계에서 실패시킨다.
## 완료 결과

- 5개 Agent와 17개 capability를 `application.yml`에 등록했다.
- `AgentOrchestrationProperties`, `AgentCapability`, `AgentRegistry`를 추가했다.
- Tool별 인자 처리를 `AgentArgumentResolver`와 기본 구현으로 분리했다.
- `AgentRouter`의 Tool 목록과 Tool별 switch를 제거하고 Registry 기반 선택으로 전환했다.
- 설정 바인딩, Registry와 기존 routing 계약 테스트를 추가했다.
- ADR-004와 구성·MCP 참조 문서를 갱신했다.

## 검증 결과

- PowerShell 정적 계약 검사: 통과. Agent 5개, capability 17개, 고유 Tool 17개와 필수 capability ID를 확인했다.
- 설정에 사용된 13개 Resolver 이름이 기본 Resolver 구현에 모두 존재함을 확인했다.
- `AgentRouter`에 기존 `EXPLICIT_TOOL_NAMES`, `TABLE_NAME_TOOLS`, Tool별 switch가 남아 있지 않음을 확인했다.
- 대상 Gradle 테스트: `--no-daemon`으로 180초 실행했으나 JVM 초기 출력 없이 시간 초과되어 테스트 리포트가 생성되지 않았다.
- YAML parser 추가 검증: 현재 PowerShell에 `ConvertFrom-Yaml`이 없고 Python 프로세스도 환경 정체로 시간 초과되어 수행하지 못했다. 들여쓰기·개수·필드 대응은 PowerShell로 검사했다.
- `verifyAll`: 동일한 외부 프로세스 기동 정체 때문에 실행하지 못했다. 환경 정상화 후 재실행이 필요하다.