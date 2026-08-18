# ADR-009: 로그인 도입 전 Agent configuration API feature gate

- 상태: Superseded
- 일자: 2026-08-18
- 대체 결정: ADR-008의 공유 token 인증
- 후속 결정: ADR-010의 localhost 전용 기본 활성화

## 배경

현재 애플리케이션에는 로그인과 사용자 권한 모델이 없다. 별도의 공유 관리자 token 입력은 사용자 로그인 흐름과 분리된 임시 credential을 추가해 운영 복잡성을 높인다. 현재 단계에서는 사용자의 요청에 따라 token 인증을 사용하지 않는다.

## 결정

1. Agent configuration 관리 API의 token header와 비교 로직을 제거한다.
2. AGENT_CONFIGURATION_ADMIN_API_ENABLED만 API 접근 gate로 사용하고 기본값은 false로 유지한다.
3. flag가 false이면 모든 조회·저장·refresh 요청을 503으로 거부하고 service를 호출하지 않는다.
4. flag를 true로 설정한 API는 무인증이므로 로컬 또는 신뢰된 개발망에서만 사용한다.
5. application.yml의 admin-token 항목은 로그인/권한 기능 도입 시 재검토할 bootstrap placeholder로 주석 처리한다.
6. 로그인 기능 도입 시 이 결정을 SSO/RBAC 기반 인증·인가 ADR로 대체한다.

## 결과

- 화면은 token 입력 없이 활성 설정을 바로 조회하고 편집한다.
- DB version 저장, validation과 Memory Snapshot 즉시 반영은 기존과 동일하다.
- 운영 환경에서 flag를 잘못 활성화하면 임의 정책 변경 위험이 있으므로 배포 기본값은 계속 false다.

## 대안

- 공유 token 유지는 현재 사용자 흐름과 맞지 않아 제외했다.
- API를 항상 활성화하는 방식은 fail-open이므로 제외했다.
