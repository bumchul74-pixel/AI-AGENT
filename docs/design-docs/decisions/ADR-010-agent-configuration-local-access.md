# ADR-010: localhost 전용 Agent configuration API 기본 활성화

- 상태: Accepted
- 일자: 2026-08-18
- 대체 결정: ADR-009의 기본 비활성화 feature gate

## 배경

Agent 설정 관리 화면은 ACTIVE 설정을 먼저 조회해야 편집, 추가, 삭제 UI를 구성할 수 있다. token 인증을 제거한 뒤에도 API 기본값이 false로 남아 최초 조회가 503으로 거부되었고 편집 영역도 렌더링되지 않았다.

## 결정

1. 로그인 기능이 없는 현재 개발 단계에서는 AGENT_CONFIGURATION_ADMIN_API_ENABLED 기본값을 true로 한다.
2. HTTP remote address가 IPv4 또는 IPv6 loopback인 요청만 허용하고 그 외 요청은 403으로 거부한다.
3. 환경변수를 false로 지정하면 localhost 요청도 포함해 모든 조회, 저장, refresh API를 503으로 차단한다.
4. 화면은 조회 실패 후 다시 불러오기를 제공하고 Agent와 Capability 삭제 버튼 및 저장 시점의 반영 방식을 명시한다.
5. 삭제는 기존 version을 물리 삭제하지 않고 항목을 제외한 새 immutable version을 저장·활성화한다.

## 결과

- 로컬 개발자는 별도 token이나 환경변수 없이 Agent 설정을 조회·저장·삭제할 수 있다.
- 원격 무인증 요청은 기본 차단된다.
- reverse proxy가 localhost에서 요청을 전달하는 배포에서는 이 제한만으로 인증 경계를 보장할 수 없으므로 API를 비활성화해야 한다.
- 저장 transaction과 commit 이후 Memory Snapshot 게시 순서는 유지된다.

## 대안

- 기본 비활성화를 유지하는 방식은 화면 중심 DB 관리 요구를 충족하지 못해 대체했다.
- 기존 version 물리 삭제는 실행 이력의 configuration version 참조를 훼손하므로 제외했다.