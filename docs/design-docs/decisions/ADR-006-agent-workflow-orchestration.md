# ADR-006: 설정 기반 Agent workflow orchestration

- 상태: Accepted
- 일자: 2026-08-18

## 배경

ADR-005는 하나의 요청을 하나의 Agent capability로 실행하고 최상위 실행 이력을 남기는 경계를 도입했다. 데이터베이스 metadata 조회 후 mapper 생성처럼 선행 단계가 필요한 작업, 서로 독립적인 여러 조회, 일시적인 읽기 실패에 대한 제한적 복구, 사용자에게 실행 과정을 설명할 수 있는 상태 모델이 추가로 필요하다.

## 결정

1. AgentPlanner는 메시지에 명시된 tool과 활성 Agent configuration Snapshot의 dependencies을 사용해 결정적인 DAG를 만든다. LLM이 임의로 계획을 만들지 않는다.
2. AgentOrchestrator는 의존성이 성공한 준비 단계만 max-parallelism 범위에서 병렬 실행한다. 실패한 의존성을 가진 단계는 SKIPPED 처리한다.
3. 재시도는 capability의 maxAttempts와 retryBackoffMs가 명시된 경우에만 수행한다. 운영 설정에서는 멱등 읽기 capability에만 재시도를 활성화한다.
4. 기본 capability가 최종 실패하면 설정된 fallbackCapabilityIds를 순서대로 실행한다. 자동 fallback이 의미상 동등함을 보장할 수 없으므로 운영 기본값은 비어 있다.
5. 채팅 응답은 execution id, capability id, 의존성, 상태, 시도 횟수, fallback id, 소요 시간만 반환한다. 요청 원문, tool 인자·결과, 로컬 경로는 상태 DTO에 포함하지 않는다.
6. 기존 agent_execution_history는 workflow 전체의 시작·성공·실패를 기록한다. 단계 상태는 현재 요청 응답에 포함하며 대화 이력 재조회에는 복제하지 않는다.

## 결과

- 단일 tool 요청은 기존과 동일한 한 단계 계획으로 실행된다.
- 명시된 여러 tool은 독립적인 경우 병렬 실행될 수 있다.
- 구성 오류, 알 수 없는 dependency/fallback, cycle은 실행 전에 실패한다.
- 프로세스 내부 실행이므로 재시작 후 resume, 분산 worker, 승인 workflow는 제공하지 않는다.

## 대안

- LLM 기반 자율 planner는 예측 가능성과 검증 가능성이 낮아 제외했다.
- 모든 실패에 대한 공통 retry는 비멱등 tool의 중복 부작용 위험 때문에 제외했다.
- 단계별 원문 결과 영속화는 보안 정책과 저장 비용 때문에 제외했다.