# AI-AGENT Harness Engineering 구성

## 목표

MCP-GATEWAY의 harness engineering 원칙을 Spring Boot, React/Vite, Python RAG 구성에 맞게 적용한다.

## 제약과 비목표

- 기존 feature 구현과 사용자의 작업 중 변경을 수정하지 않는다.
- Legacy adapter 전용 문서, Docker gate, Jenkins 배포를 복사하지 않는다.
- 아직 없는 운영 SLO나 coverage를 달성한 것으로 표현하지 않는다.

## 구현

- `AGENTS.md`를 canonical 작업 진입점으로 확장했다.
- 아키텍처, 제품, 설계, 보안, 신뢰성, 품질 문서 계층을 만들었다.
- 실행 계획과 기술부채 workflow를 만들었다.
- Java scope, RAG unittest, frontend build, 문서 검증, `doctor`, `verifyAll`을 추가했다.
- PR과 issue 검토 템플릿을 추가했다.

## 검증

- [x] `.\gradlew.bat validateHarnessDocs`
- [x] `.\gradlew.bat test`
- [x] `.\gradlew.bat ragTest` (15 tests)
- [x] `.\gradlew.bat frontendCheck`
- [x] `.\gradlew.bat verifyAll`
