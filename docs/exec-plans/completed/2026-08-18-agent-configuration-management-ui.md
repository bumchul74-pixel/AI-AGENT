# Agent configuration 관리 API와 화면

## 목표

운영 관리 메뉴에서 Agent, capability, dependency, retry, fallback 정책을 조회·수정하고 새 DB version으로 저장·활성화하여 다음 업무 요청부터 Memory Snapshot으로 즉시 사용한다.

## 제약

- Controller는 인증과 HTTP mapping만 담당하고 저장 transaction과 Memory publish는 기존 AgentConfigurationService가 담당한다.
- 관리자 credential은 환경변수로 주입하고 DB, 로그, frontend storage에 저장하지 않는다.
- 인증 설정이 없으면 관리 API는 fail-closed 한다.
- UI 성공·실패 feedback은 전역 Toast로 표시한다.
- 실행 중 workflow는 기존 configuration version을 끝까지 유지한다.

## 비목표

- 사용자/역할 DB와 SSO 구축
- 여러 ACTIVE version 허용
- 실행 중 workflow 정책 교체
- configuration version 삭제

## 현재 상태

DB 저장·활성화와 Memory Snapshot 게시 서비스는 있으나 외부 관리 API와 화면 메뉴가 없다.

## 단계

1. 관리자 token bootstrap 설정과 constant-time 인증 경계를 추가한다.
2. 활성 설정 조회, 저장·활성화, refresh API와 DTO를 추가한다.
3. 운영 관리 메뉴에 구조화된 Agent configuration 편집 화면을 추가한다.
4. 제품·보안·신뢰성·설정 문서와 ADR을 갱신한다.
5. 인증, API, 서비스, frontend build와 verifyAll을 검증한다.

## 검증

- admin API disabled, token 누락/불일치, 정상 token
- 정상 저장 시 새 DB version과 local Memory version 일치
- invalid configuration 저장 거부와 기존 Snapshot 유지
- frontend production build
- verifyAll

## 위험

- 공유 관리자 token의 사용자별 감사 한계
- 잘못된 정책 활성화에 따른 이후 요청 실패
- 여러 instance 간 polling 지연

## Rollback

AGENT_CONFIGURATION_ADMIN_API_ENABLED=false로 관리 API를 차단한다. 설정 문제는 이전 configuration 문서를 새 version으로 저장하거나 DB ACTIVE version을 복구한 후 refresh한다. DB 기능 전체 장애 시 기존 bootstrap fallback을 사용한다.

## 결정

- 현재 인증 인프라가 없으므로 단일 관리자 token을 임시 운영 인증 경계로 사용한다.
- token은 X-Agent-Configuration-Admin-Token header로 전달하고 frontend memory에만 보관한다.
- 저장은 수정 update가 아닌 새 immutable version 생성과 활성화로 수행한다.

## 완료 증거

- 관리자 인증, Controller, DB/Memory 서비스 대상 테스트: BUILD SUCCESSFUL
- frontendCheck: Vite production build 성공
- .\gradlew.bat verifyAll --console=plain: BUILD SUCCESSFUL (2m 20s)
- Java test/integrationTest, Python RAG 16 tests, validateHarnessDocs 통과
- 브라우저 시각 점검은 Windows sandbox CreateProcessWithLogonW 1385로 실행하지 못했으며 production build로 대체 검증
- Vite 500 kB chunk 경고는 기존 비차단 경고로 유지
