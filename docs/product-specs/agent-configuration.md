# Agent configuration 관리

## 목적

운영 Agent, capability, 의존성, 병렬 실행, 재시도와 대체 Agent 정책을 재배포 없이 변경하고 적용 version을 추적한다.

## 동작

- DB의 단일 ACTIVE version이 정상 운영 원본이다.
- 내부 저장·활성화 유스케이스는 전체 설정을 먼저 검증하고 DB transaction을 commit한 뒤 local Memory Snapshot을 즉시 교체한다.
- application 업무 경로는 Memory Snapshot만 읽는다.
- polling mode에서는 다른 instance 또는 외부에서 활성화된 version을 refresh interval 안에 읽어 검증한 뒤 반영한다.
- workflow는 계획 시점의 configuration version과 Snapshot을 실행 종료까지 유지하고 실행 이력에 version을 기록한다.
- 빈 DB는 seed 설정에 따라 bundled bootstrap으로 초기화한다.

## 관리 화면

- 위치: 운영 관리 > Agent 설정 관리
- 로컬 관리 API는 기본 활성화되며 현재 ACTIVE version과 source를 표시한다. 원격 요청은 거부한다.
- maxParallelism과 Agent 기본 정보, Capability의 Tool, intent, resolver, 우선순위, timeout, dependency, retry, fallback, 승인 여부를 편집한다.
- 저장 및 활성화는 기존 row를 수정하지 않고 새 version을 생성한다.
- Agent와 Capability 삭제는 편집 목록에서 제거한 뒤 저장 및 활성화할 때 새 version에 반영하며 이전 version은 이력으로 보존한다.
- DB 다시 반영은 현재 DB ACTIVE version을 검증하고 local Memory Snapshot에 게시한다.
- 성공·실패는 전역 Toast로 알리고 같은 오류를 화면 내부에 중복 표시하지 않는다.
## 실패 조건

- 빈 capability, 중복 capability ID 또는 tool
- 지원하지 않는 executor, MCP server 또는 argument resolver
- 알 수 없는 dependency/fallback, 자기 참조 또는 dependency cycle
- JSON decode 실패 또는 DB metadata와 문서의 maxParallelism 불일치

검증 또는 refresh가 실패하면 새 설정을 게시하지 않고 last-known-good Snapshot을 유지한다. 시작 fallback이 비활성화된 상태에서 ACTIVE version을 읽을 수 없으면 애플리케이션 기동을 실패시킨다.

## 비범위

- 사용자별 계정, SSO와 역할 기반 권한 관리
- 실행 중 workflow 정책 교체
- instance 간 강한 동시 일관성
