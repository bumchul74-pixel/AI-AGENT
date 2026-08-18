# Agent workflow orchestration 확장

## 목표

기존 단일 작업 `AgentOrchestrator`를 설정 기반 다단계 DAG 실행기로 확장하고, 의존성·병렬 실행·재시도·대체 capability 정책과 실행 단계 상태 UI를 제공한다.

## 제약

- `AgentRouter`는 선택과 인자 해석만 담당하며 외부 호출을 수행하지 않는다.
- retry는 설정에서 명시한 capability에만 적용한다.
- fallback은 동일 입력 계약을 사용할 수 있도록 명시된 capability ID만 사용한다.
- 요청 원문, Tool 인자/결과와 로컬 경로는 실행 이력에 저장하지 않는다.
- UI 성공·부분 실패·오류는 기존 전역 Toast 정책을 유지한다.

## 비목표

- LLM이 임의로 계획을 생성하는 자율 Planner
- 장기 실행 queue, 분산 worker, 중단 후 resume
- destructive Tool 자동 승인

## 현재 상태

요청당 하나의 `AgentRoute`를 동기 실행하고 최상위 성공·실패 이력만 저장한다. 단계별 이력과 dependency, 병렬·retry·fallback 정책 및 UI가 없다.

## 단계

1. 기존 단일 작업 동작을 회귀 검증한다.
2. capability dependency와 DAG Planner, 단계 이력을 추가한다.
3. 준비된 단계의 제한 병렬 실행과 retry/fallback 정책을 추가한다.
4. Chat 응답에 실행 요약을 포함하고 Frontend에서 단계와 상태를 표시한다.
5. 제품·아키텍처·신뢰성·보안 문서 및 ADR을 갱신하고 `verifyAll`을 실행한다.

## 검증

- 단일 작업 호환성, dependency 순서, 독립 단계 병렬성
- retry 횟수, fallback 성공, dependency 실패 시 skip
- 단계 이력에서 요청/인자/결과/경로 비저장
- Chat DTO 및 Frontend production build
- `verifyAll --console=plain`

## 위험

- 잘못된 dependency 설정은 cycle 또는 실행 불가 계획을 만들 수 있다.
- 무제한 병렬 실행은 MCP 서버를 과부하시킬 수 있다.
- retry가 비멱등 Tool에 적용되면 중복 부작용이 발생할 수 있다.
- 실행 결과를 UI DTO에 포함하면 민감정보가 노출될 수 있다.

## Rollback

Provider를 단일 route 실행으로 되돌리고 새 단계 테이블과 Planner/정책 클래스를 제거한다. 추가된 nullable 설정과 단계 테이블은 기존 단일 실행을 방해하지 않는다.

## 결정

- 계획은 설정된 dependency와 메시지에 명시된 Tool을 기반으로 결정적으로 생성한다.
- 준비된 DAG 단계만 최대 병렬도 이내에서 실행한다.
- 단계 DTO에는 결과 본문 없이 상태, attempt, fallback 여부와 시간만 노출한다.

## 완료 결과

- 단일 작업 AgentOrchestrator와 최상위 실행 이력을 유지하면서 설정 기반 AgentPlanner와 dependency DAG를 추가했다.
- 준비된 독립 단계는 max-parallelism 범위에서 병렬 실행하고, 의존 실패 후속 단계는 SKIPPED 처리한다.
- capability별 max-attempts, retry-backoff, fallback-capabilities 정책과 안전한 상태 DTO를 추가했다.
- 채팅 즉시 응답에 접을 수 있는 실행 단계 패널을 추가했다.
- dependency 순서, 병렬 overlap, retry 횟수, fallback 성공과 기존 단일 실행 회귀를 테스트했다.
- 2026-08-18: gradlew verifyAll --console=plain 성공. Java test/integrationTest, 16 RAG tests, frontendCheck, validateHarnessDocs, doctor가 통과했다.
- liveTest는 실제 외부 LLM/MCP/DB 호출 권한이 필요하고 deterministic gate 범위가 아니므로 실행하지 않았다.