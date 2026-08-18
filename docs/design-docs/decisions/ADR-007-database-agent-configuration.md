# ADR-007: DB 기반 Agent configuration과 Memory Snapshot

- 상태: Accepted
- 일자: 2026-08-18
- 관련 결정: ADR-004, ADR-006

## 배경

Agent와 capability 정책을 application.yml에서만 관리하면 변경마다 재배포와 재시작이 필요하고, 적용 버전과 실행 이력을 연결하기 어렵다. 반대로 매 요청마다 DB를 조회하면 라우팅 경로가 저장소 가용성과 지연에 직접 의존한다.

## 결정

1. versioned agent_configuration_version aggregate를 Agent orchestration 정책의 원본으로 사용한다. 한 시점에는 하나의 ACTIVE version만 허용한다.
2. 저장 요청은 전체 문서를 검증하고 DRAFT 저장, 기존 ACTIVE 보관, 새 version 활성화를 하나의 transaction에서 수행한다. commit이 끝난 뒤 해당 process의 불변 Memory Snapshot을 원자적으로 교체한다.
3. 업무 요청은 DB를 직접 읽지 않고 시작 시점의 Memory Snapshot을 캡처한다. 실행 중 설정이 교체되어도 해당 workflow는 같은 version과 병렬도, dependency, retry, fallback 정책을 끝까지 사용한다.
4. 다른 instance나 외부 DB 활성화는 polling refresh로 감지한다. decode 또는 validation 실패 시 last-known-good Snapshot을 유지한다.
5. application.yml에는 DB 기능, bootstrap seed, 시작 fallback, refresh mode/interval, bootstrap resource 위치만 남긴다. bundled bootstrap JSON은 빈 DB 초기 seed와 장애 복구에만 사용한다.
6. 구성 문서에는 credential, prompt, 요청 원문, Tool 인자·결과, 로컬 경로를 저장하지 않는다. 인증·권한·감사 정책이 마련되기 전에는 외부 configuration 쓰기 endpoint를 노출하지 않는다.

## 결과

- 정상 저장은 같은 instance의 다음 업무부터 즉시 반영되고, 다른 instance는 polling 주기 안에 eventual consistency로 반영된다.
- DB 장애나 잘못된 외부 변경이 현재 업무 경로를 즉시 중단하지 않는다.
- 실행 이력의 configuration_version으로 적용 정책을 추적할 수 있다.
- 직접 DB 수정은 애플리케이션 검증을 우회할 수 있으므로 운영 변경은 내부 서비스 경계를 사용해야 한다.

## 대안

- YAML hot reload는 versioning, transaction, 다중 instance 동기화를 별도로 구현해야 해서 선택하지 않았다.
- 요청별 DB 조회는 장애 격리와 latency 측면에서 제외했다.
- DB trigger로 JSON 의미 검증을 구현하는 방식은 Java resolver와 capability 규칙을 중복하므로 제외했다.
