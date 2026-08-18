# Neo4j 데이터 탐색기

## 목표

Java Graph와 분리된 조회 전용 Neo4j 노드 목록·상세 화면을 제공한다.

## 제약

- 서버 사이드 0-based 페이지네이션과 안정 정렬을 사용한다.
- Neo4j elementId를 상세 식별자로 사용한다.
- 수정·삭제 기능은 제공하지 않는다.
- credential 성격의 속성은 응답에서 마스킹한다.

## 단계

1. 조회 Repository, Service, REST API 구현
2. 메뉴와 목록·상세 화면 구현
3. 테스트와 문서 갱신
4. verifyAll 실행

## 검증

- 페이지 범위 정규화와 표준 page metadata
- 상세 not found
- frontendCheck와 verifyAll

## Rollback

메뉴와 API를 제거한다.
## Completion evidence

- Implemented read-only `neo4jexplorer` controller, service, repository, DTOs, and pagination tests.
- Added Operations menu page with filtering, server pagination, node properties, and relationship navigation.
- Added sensitive-property masking and a 500-relationship response cap.
- `gradlew test --tests com.hanwha.ai.neo4jexplorer.service.Neo4jExplorerServiceTest`: passed.
- `gradlew frontendCheck`: passed.
- `gradlew verifyAll`: passed on 2026-08-18; external Neo4j live connectivity was not invoked by the deterministic gate.
## Follow-up: append pagination and Korean text

- Replaced JSX-visible Unicode escape text with actual UTF-8 Korean.
- Replaced explicit page navigation with the project-standard append flow: page size 30, 80px end threshold, stable-ID deduplication, and in-flight locking.
- Added an accessible manual load-more fallback and documented the shared frontend pagination standard.
- `gradlew frontendCheck`, `validateHarnessDocs`, and `verifyAll` passed on 2026-08-18.
## Follow-up: shared list layout and fixed header

- Confirmed the page is hosted by the application `MainLayout`.
- Replaced the page-specific list shell with the shared `ScrollableListLayout`.
- Fixed `thead` and `th` inside the independent list scroll container.
## Follow-up: opaque shared DataTable header

- Added a shared `DataTable` that owns table, header, body, and row iteration.
- Migrated Neo4j explorer and integrated data cleanup tables to the shared component.
- Replaced the undefined transparent header background with the opaque theme surface and standardized stacking and visual separation.