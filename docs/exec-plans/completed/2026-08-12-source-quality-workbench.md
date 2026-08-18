# 소스 품질 분석·추이·Gate

## 목표

Java 메서드 품질 metadata를 실제 메서드 단위 Vector 인덱스로 사용하고, 프로젝트별 중복·복잡도 조회, 품질 화면, 추이와 임계치 Gate를 제공한다.

## 제약

- 1단계 기존 JavaParser 품질 계산 계약을 유지한다.
- Java 파일은 일반 문자 chunk를 만들지 않고 java-method chunk만 저장한다.
- 품질 API는 methodBody와 normalizedBody를 응답하지 않는다.
- Neo4j는 현재 품질 원천, PostgreSQL은 임계치와 snapshot 이력을 담당한다.
- 외부 시스템 없는 deterministic 테스트를 유지한다.

## 비목표

- Git commit/branch와 snapshot 연결
- 언어별 품질 분석 확장
- SonarQube 규칙 호환
- 운영 배포 환경의 기존 데이터 자동 재색인

## 현재 상태

- Method 노드에 위치, hash, 복잡도 지표가 저장된다.
- java-method Vector chunk가 일반 Java chunk에 추가로 저장된다.
- 품질 조회 API, 화면, 추이와 Gate는 없다.

## 단계

1. 기존 Method 품질 필드와 테스트를 재검증한다.
2. Java 파일은 일반 chunk 대신 java-method chunk만 저장한다.
3. Neo4j Method 품질 조회 port와 중복·고복잡도 API를 구현한다.
4. React 소스 품질 화면에서 요약, 중복 그룹, 복잡도 순위, 추이를 제공한다.
5. PostgreSQL에 프로젝트별 임계치와 품질 snapshot을 저장하고 Gate를 계산한다.

## 검증

- RagVectorIngestTask Java 전용 chunk 계약 테스트
- SourceQualityService 중복·복잡도·Gate 테스트
- SourceQualityController API 계약 테스트
- mapper/schema 문서 검증
- frontendCheck
- verifyAll

## 위험

- 메서드 전용 전환 후 본문 밖 class-level 문맥 검색 품질이 낮아질 수 있다.
- 대규모 프로젝트의 Method 전체 조회 비용이 증가할 수 있다.
- 파일별 재색인 중간 snapshot이 품질 추이에 포함될 수 있다.

## Rollback

품질 API와 화면을 제거하고 Java 일반 chunk ingest를 복원한다. PostgreSQL 품질 테이블은 다른 기능과 독립적이므로 읽기를 중단한 뒤 별도 migration에서 제거할 수 있다.

## 결정

- 정확 중복은 methodHash, 구조 중복은 structuralHash 기준으로 그룹화한다.
- 기본 임계치는 cyclomatic 10, cognitive 15, duplicate ratio 10%, 최소 중복 라인 5이다.
- Gate는 임계치 초과 메서드가 있거나 중복 비율이 기준을 초과하면 FAIL이다.
- snapshot은 품질 요약이 직전 값과 달라질 때만 추가한다.

## Validation result

- SourceQualityService, SourceQualityController, RagVectorIngestTask, JavaSourceGraphAnalyzer and JavaMethodVectorChunkFactory tests passed.
- `frontendCheck` passed with the existing Vite chunk-size warning.
- `verifyAll --console=plain` passed: Java tests, integration tests, 16 Python RAG tests, frontend production build and harness documentation validation.
- No live Neo4j, PostgreSQL, VectorDB, LLM or MCP call was executed.
