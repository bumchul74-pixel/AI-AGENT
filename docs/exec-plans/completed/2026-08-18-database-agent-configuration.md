# Database-backed Agent configuration

## 목표

Agent orchestration 구성의 원본을 application.yml에서 versioned DB configuration으로 이동하고, 검증된 불변 Snapshot을 Memory에 원자적으로 게시하여 재시작 없이 이후 업무 요청에 반영한다.

## 제약

- application.yml에는 DB 구성 기능 활성화, bootstrap seed, refresh mode/interval, 장애 fallback 같은 최소 운영 설정만 둔다.
- 실행 중인 workflow는 시작 시점 configuration version을 끝까지 사용한다.
- 알 수 없는 resolver, 중복 capability/tool, dependency/fallback 참조 오류와 cycle은 게시 전에 차단한다.
- DB 또는 refresh 실패 시 마지막 정상 Snapshot을 유지한다.
- credential, prompt, tool argument/result, 로컬 경로를 구성 문서나 로그에 저장하지 않는다.

## 비목표

- 분산 consensus 또는 모든 인스턴스의 동시 원자 교체
- Agent 관리 프런트엔드
- 실행 중 workflow의 configuration hot replacement

## 현재 상태

Agent와 capability는 application.yml의 agent.orchestration.agents에 있으며 Spring 시작 시 불변 AgentRegistry 하나로 변환된다. 운영 중 변경, version, rollback, refresh 경계가 없다.

## 단계

1. versioned Agent configuration DB 스키마와 MyBatis repository를 추가한다.
2. bundled bootstrap 문서와 최소 application.yml 운영 설정을 추가한다.
3. AgentRegistry를 versioned immutable Snapshot provider로 전환한다.
4. 저장·활성화·after-commit memory publish와 polling refresh를 구현한다.
5. configuration version을 계획과 실행 이력에 연결한다.
6. 내부 관리 서비스, 문서, 테스트를 추가하고 verifyAll을 실행한다.

## 검증

- 빈 DB bootstrap seed와 활성 version 로드
- 유효하지 않은 구성 게시 차단
- 저장 후 Memory Snapshot 즉시 교체
- refresh 실패 시 last-known-good 유지
- workflow 시작 후 registry 변경에도 기존 version 유지
- test, integrationTest, frontendCheck, validateHarnessDocs, verifyAll

## 위험

- DB commit 후 local Memory 게시 실패
- 여러 인스턴스 사이 짧은 eventual-consistency 구간
- 직접 DB 수정으로 validation을 우회
- bootstrap 문서와 운영 DB 초기 version의 차이

## Rollback

agent.orchestration.config.database-enabled를 false로 설정하면 bundled bootstrap Snapshot만 사용한다. DB refresh 실패 시 기존 Memory Snapshot을 유지하며 fallback-on-startup이 활성화된 경우 bootstrap으로 기동한다.

## 결정

- 구성은 version별 JSON aggregate로 저장해 활성 pointer 전환과 rollback을 단순화한다.
- application 요청은 DB를 직접 조회하지 않고 Memory Snapshot만 참조한다.
- 관리 서비스 경유 변경은 commit 후 즉시 local Snapshot을 교체한다.
- 외부 DB 변경과 다른 인스턴스는 polling으로 활성 version을 감지한다.
- 인증·권한 경계가 정해지기 전에는 외부 쓰기 API를 노출하지 않는다.

## 완료 증거

- Agent configuration 대상 테스트: BUILD SUCCESSFUL
- .\gradlew.bat verifyAll --console=plain: BUILD SUCCESSFUL (1m 21s)
- Java test/integrationTest, Python RAG 16 tests, frontend Vite build, validateHarnessDocs 통과
- Vite bundle size 경고는 기존 비차단 경고로 유지
