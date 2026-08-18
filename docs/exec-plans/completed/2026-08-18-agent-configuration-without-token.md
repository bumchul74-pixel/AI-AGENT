# Agent configuration token 제거

## 목표

로그인 기능이 없는 현재 단계에서 Agent 설정 관리 화면의 공유 token 입력과 header 검증을 제거하고, 명시적인 API 활성화 flag만 유지한다.

## 제약

- 관리 API는 기본 비활성화한다.
- 활성화된 API는 무인증 쓰기 경계이므로 신뢰된 개발 환경에서만 사용한다.
- DB transaction, validation, Memory publish 순서는 변경하지 않는다.
- 로그인/권한 도입 시 token 또는 SSO/RBAC 경계를 다시 설계한다.

## 비목표

- 로그인, 사용자, 역할 관리 구현
- Agent configuration 저장 모델 변경

## 단계

1. backend token 속성·header 검증을 제거하고 enable guard만 유지한다.
2. frontend token 입력·보관·잠금 UI를 제거한다.
3. 테스트와 보안·설정·ADR 문서를 갱신한다.
4. verifyAll을 실행한다.

## 검증

- API disabled 시 503과 service 미호출
- API enabled 시 조회·저장·refresh 허용
- frontend production build
- verifyAll

## 위험

- flag를 활성화하면 인증 없이 실행 정책을 변경할 수 있다.
- 운영망 오노출 시 임의 정책 변경 위험이 있다.

## Rollback

AGENT_CONFIGURATION_ADMIN_API_ENABLED=false로 즉시 API를 차단한다. 로그인 기능 도입 후 ADR-009를 대체하는 인증 결정을 추가한다.

## 완료 증거

- feature gate와 Controller 대상 테스트: BUILD SUCCESSFUL
- frontendCheck: Vite production build 성공
- .\gradlew.bat verifyAll --console=plain: BUILD SUCCESSFUL (1m 54s)
- Java test/integrationTest, Python RAG 16 tests, validateHarnessDocs 통과
- Vite 500 kB chunk 경고는 기존 비차단 경고로 유지
