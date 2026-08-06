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
