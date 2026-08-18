# 프런트엔드 가이드

React/Vite 프런트엔드는 백엔드 API를 사용해 대화, 생성, 문서, 이력, source graph, secure coding 기능을 표시한다.

## 경계

- `api`: HTTP 호출과 wire format
- `components`: 재사용 가능한 표현
- `pages`: 화면 조합과 route-level 상태
- `hooks`: API 상태와 UI workflow
- `constants`, `utils`, `routes`, `store`: 공통 지원

생성 규칙, RAG ranking, 권한 판단, DB 규칙을 프런트엔드에 구현하지 않는다. 오류 응답은 이해 가능한 상태로 표시하되 credential이나 내부 stack trace를 노출하지 않는다.

검증은 `.\gradlew.bat frontendCheck`로 실행한다. lint, component, 접근성 검증은 [기술부채 추적기](exec-plans/tech-debt-tracker.md)에서 관리한다.

## 사용자 피드백

처리 성공, 부분 실패, 오류 메시지는 모두 전역 Toast UI로 표시한다. Toast의 위치, 표시 시간, 닫기 동작은 동일하게 유지하고 `success`, `warning`, `error` 상태는 색상과 아이콘으로 구분한다. 같은 피드백을 화면 내부 인라인 메시지와 Toast에 중복 노출하지 않는다.

필수 입력 누락, 검색어 미입력, 프로젝트·파일 미선택 같은 클라이언트 입력 검증 메시지도 인라인 문구나 브라우저 알림 대신 `warning` Toast로 표시한다.

## 공통 레이어 팝업

- 레이어 팝업은 `frontend/src/components/common/Modal.jsx`를 사용한다. 화면별 backdrop, Escape 처리, body scroll 잠금, 제목 영역과 닫기 버튼을 중복 구현하지 않는다.
- Modal은 `modal-header`와 `modal-body`의 두 영역으로 구성한다. 제목과 닫기 버튼이 있는 헤더는 고정하고, 콘텐츠가 가용 높이를 초과할 때는 `modal-body`만 독립적으로 스크롤한다.
- 팝업별 너비와 콘텐츠 표현은 `className`으로 확장할 수 있지만, 공통 헤더·본문 구조와 닫기 동작을 변경하지 않는다.
- 화면 안에 상시 배치되는 상세 패널은 Modal로 표기하지 않는다. 실제 backdrop 레이어로 열리는 대화상자에만 `role="dialog"`와 `aria-modal="true"`를 적용한다.
## Agent 실행 단계 표시

AI 채팅의 즉시 응답에 agentExecution이 있으면 메시지 아래에 접을 수 있는 실행 단계 패널을 표시한다. 패널은 전체 상태와 단계별 capability, dependency, retry/fallback, 소요 시간만 보여준다. 기존 대화 이력 응답에는 실행 상세를 복제하지 않는다.

## Agent 설정 관리

운영 관리 메뉴의 Agent 설정 관리 화면은 별도 token 입력 없이 활성 설정을 조회한다. 편집 form은 backend configuration aggregate의 표현이며 validation, version 생성, 활성화와 실행 정책 판단을 복제하지 않는다. 저장·refresh 성공과 오류는 공통 Toast를 사용한다.

AI 대화의 `@` Tool Auto Complete는 `/api/mcp/agent-tools`를 통해 DB ACTIVE Agent configuration의 Memory Snapshot을 조회한다. 프런트엔드는 Tool 필터와 선택만 담당하고 활성 Agent/Capability 판단이나 필수 인자 규칙을 복제하지 않는다.
## 목록 페이징 표준

대용량 목록은 공통 `components/common/ScrollableListLayout.jsx`를 사용하고 표 형식 목록은 반드시 `components/common/DataTable.jsx`로 구성한다. 공통 레이아웃은 독립 스크롤 영역과 목록 footer 구조를 제공한다. `DataTable` 헤더는 불투명한 `surface-subtle` 배경, 본문보다 높은 stacking level, 하단 경계와 그림자를 적용하여 스크롤 중 행 데이터가 헤더에 비치거나 겹치지 않게 한다. 목록은 30건 단위 서버 페이지를 사용하고, 사용자가 목록 하단 80px 이내에 도달하면 다음 페이지를 조회하여 기존 목록에 중복 없이 append한다. 필터 변경 시 0페이지부터 다시 조회하고, 중복 요청 잠금과 `last`/`hasNext` 종료 조건을 적용한다. 세부 구현 규칙은 [프런트엔드 목록 페이징 표준](references/frontend-list-pagination.md)을 따른다.
## 좌측 메뉴 정보구조

Dashboard는 독립 직접 이동 메뉴이며, 나머지 1Depth는 업무 목적별 `group`, 2Depth는 실제 화면으로 구성한다. Depth는 자식 개수로 추론하지 않고 navigation 정의의 `type`으로 명시한다. RAG와 Neo4j 조회는 `데이터 탐색`, 프로젝트와 문서는 `지식 관리`, 상태·설정·삭제는 `운영 관리`에 둔다. 전체 구조와 분류 기준은 [좌측 메뉴 정보구조 표준](references/navigation-information-architecture.md)을 따른다.