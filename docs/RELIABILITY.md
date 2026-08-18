# 신뢰성 정책

## 장애 경계

- LLM, Python RAG, MCP, PostgreSQL, Neo4j는 독립적으로 실패할 수 있다.
- timeout, 연결 실패, 잘못된 응답은 경계에서 분류한다.
- retry는 멱등성이 보장되는 조회에만 제한한다.
- 생성 성공과 이력 저장 실패 같은 부분 실패를 구분한다.
- Agent 실행 이력 저장 실패는 외부 Agent 실행을 차단하지 않고 execution id와 오류 유형만 warning으로 기록한다.

## 관측성

correlation id, 외부 시스템 종류, 처리 단계, latency, 상태를 구조화해 기록한다. API key, token, 업로드 원문, 전체 prompt/response는 일반 로그에 기록하지 않는다.

## 검증 계층

- `test`: 외부 시스템 없는 Java 검증
- `integrationTest`: Spring wiring과 로컬 의존성
- `ragTest`: Python 검색·인덱싱 단위 동작
- `frontendCheck`: 배포 가능한 frontend bundle
- `verifyAll`: merge 전 deterministic gate
- `liveTest`: 명시적으로 구성된 실제 외부 연동

운영 SLO, error budget, RTO/RPO, alert threshold는 아직 승인되지 않았으며 [기술부채 추적기](exec-plans/tech-debt-tracker.md)에서 관리한다.

## LLM 컨텍스트 크기 제어

소스 생성용 하이브리드 검색은 선택한 지식 프로젝트 안에서만 벡터 및 소스 그래프를 확장한다. 기본값은 그래프 깊이 2, 노드 50개, 관계 200개, 증거 청크 12개, 최종 RAG 컨텍스트 60,000자이다. 증거 청크를 그래프 정보보다 먼저 배치해 컨텍스트가 잘리더라도 원문 근거가 우선 보존된다.

다음 환경 변수로 운영 한도를 조정할 수 있다.

- `RAG_HYBRID_GRAPH_DEPTH`
- `RAG_HYBRID_MAX_GRAPH_NODES`
- `RAG_HYBRID_MAX_GRAPH_RELATIONSHIPS`
- `RAG_HYBRID_MAX_EVIDENCE_CHUNKS`
- `RAG_MAX_CONTEXT_CHARACTERS`

디버그 로그에는 프로젝트 키와 각 컨텍스트 구성 요소의 길이만 기록하며 실제 프롬프트나 검색 원문은 기록하지 않는다.

## 연계 시스템 상태 점검

상태 화면의 일반 조회는 캐시된 snapshot만 반환한다. 외부 probe는 background 주기 점검과 관리자의 명시적 즉시 점검에서 수행한다. Backend와 핵심 저장소 장애는 `DOWN`, 선택 연계 장애는 `DEGRADED`, 아직 점검하지 않은 상태는 `UNKNOWN`으로 구분한다. connectivity 결과는 실제 비즈니스 흐름의 완전한 성공을 보장하지 않으므로 운영 환경에서는 실제 요청 오류율과 저빈도 synthetic test를 함께 관찰한다.

## Java 메서드 품질 인덱싱

메서드 품질 지표는 외부 호출 없이 동일한 Java source와 JavaParser 버전에서 결정적으로 계산한다. java-method chunk ID는 methodUid 기반으로 안정적으로 생성하며 source 재색인 전에 기존 sourceKey chunk를 제거해 삭제되거나 signature가 바뀐 메서드의 stale chunk를 남기지 않는다.

일반 원문 chunk 저장 후 메서드 chunk 저장이 실패하면 해당 문서의 vector 상태를 `FAILED`로 기록한다. 운영자는 원인을 해소한 뒤 문서를 재색인해야 하며, 기존 인덱스에 품질 metadata를 소급 적용할 때도 전체 source 재색인을 사용한다. 메서드 본문 metadata로 인한 저장량 증가는 source별 chunk 수와 인덱싱 latency로 관찰한다.

## Agent workflow 실행 정책

Agent DAG는 의존 단계가 모두 성공한 경우에만 후속 단계를 실행하고, 실패한 의존성을 가진 단계는 SKIPPED로 종료한다. 독립 단계는 활성 configuration의 max-parallelism 범위에서 병렬 실행한다. retry는 capability에 max-attempts가 명시된 멱등 읽기 작업에만 활성화하며, 시도 사이에는 retry-backoff를 적용한다. fallback은 명시된 capability ID 순서로만 수행한다. 일부 단계가 성공하면 PARTIAL, fallback으로 복구하면 SUCCEEDED_WITH_FALLBACK 상태를 반환한다.

## Agent configuration 복구 정책

기동 시 DB에 ACTIVE version이 없고 seed가 활성화되어 있으면 bundled bootstrap을 새 version으로 저장·활성화한다. DB 조회·decode·validation 실패 시 fallback-on-startup=true이면 bootstrap Snapshot으로 기동하고, 실행 중 polling 실패에는 last-known-good Snapshot을 유지한다. 정상 저장은 transaction commit 후 local Memory에 즉시 게시되며 다른 instance는 refresh interval 안에 반영된다. 각 workflow는 시작 시점 configuration version을 고정하므로 실행 중 refresh의 영향을 받지 않는다.

관리 화면 저장은 전체 문서를 먼저 검증하고 DB transaction commit 이후에만 local Memory를 교체한다. 인증 실패, validation 실패 또는 DB commit 실패에는 현재 Snapshot을 유지한다. 화면의 DB 다시 반영도 decode와 validation을 통과한 ACTIVE version만 게시한다.

## MCP Tool 자동 동기화

tools/list 변경은 주기적으로 DB 새 version으로 자동 활성화한다. 빈 목록, 연결 실패, validation 또는 commit 실패는 기존 Snapshot을 유지한다. 변경이 없으면 version을 생성하지 않는다. 긴급 중단은 AGENT_CONFIGURATION_TOOL_SYNC_ENABLED=false를 사용한다.
## Neo4j explorer query bounds

Node browsing uses deterministic server-side pagination ordered by display name and elementId. Page size defaults to 30 and is capped at 100. Detail responses cap adjacent relationship rows at 500 while returning the total relationship count, preventing an unbounded high-degree node response. Neo4j failures use the shared API failure path and do not fall back to stale or fabricated data.
