# 실행 계획 운영

## 계획이 필요한 변경

- backend, frontend, RAG 중 둘 이상을 변경
- 데이터 migration 또는 공개 API 계약 변경
- LLM/RAG/MCP/provider 경계 변경
- 보안·개인정보·파일 처리 경계 변경
- 여러 세션에 걸치거나 rollback이 필요한 작업

작은 오탈자, 국소 버그, 동작을 바꾸지 않는 refactoring에는 계획을 생략할 수 있다.

## 필수 항목

`docs/exec-plans/active/YYYY-MM-DD-short-name.md`에 목표, 제약, 비목표, 현재 상태, 단계, 검증, 위험, rollback, 결정을 기록한다.

완료하면 검증 결과를 기록하고, 남은 항목을 [기술부채 추적기](exec-plans/tech-debt-tracker.md)로 옮긴 뒤 계획을 `completed`로 이동한다.

## 디렉터리

- [active](exec-plans/active/README.md)
- [completed](exec-plans/completed/README.md)
- [tech-debt-tracker](exec-plans/tech-debt-tracker.md)
