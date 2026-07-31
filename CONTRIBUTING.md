# 기여 가이드

## 변경 시작

1. `AGENTS.md`와 관련 제품·설계 문서를 읽는다.
2. 하나의 결과에 집중한 branch를 사용한다.
3. 다중 구성 요소, migration, 보안 경계, 장기 작업은 `docs/PLANS.md`에 따라 실행 계획을 만든다.
4. 승인된 아키텍처 결정을 변경하면 ADR을 먼저 추가한다.

## 구현 원칙

- 검색된 표준 코드와 문서를 생성 근거로 사용한다.
- 기존 service, provider, router, workflow 확장 지점을 재사용한다.
- 민감정보와 실제 환경 endpoint를 commit하지 않는다.
- frontend, backend, RAG에 동일한 비즈니스 규칙을 중복 구현하지 않는다.
- formatting, build 결과, log, upload 파일을 feature 변경에 섞지 않는다.

## 검증과 리뷰

기본 pre-review gate는 `.\gradlew.bat verifyAll`이다. 외부 시스템 연결 검증은 `live` tag와 opt-in 설정으로 격리한다.

Pull Request에는 문제와 비목표, 영향 경계, RAG 근거 변화, 구성·보안·신뢰성 영향, 검증 결과, rollout/rollback, 남은 기술부채를 기록한다.
