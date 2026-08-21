# ADR-002: 검증 범위 분리

- 상태: Accepted
- 날짜: 2026-07-31

## 상황

일부 test는 Spring context 또는 외부 시스템을 요구한다. 이를 기본 test에 섞으면 환경 의존적인 gate가 된다.

## 결정

- `test`는 `integration`, `live` tag를 제외한다.
- `integrationTest`는 `integration`만 포함하고 `live`를 제외한다.
- `liveTest`는 `live`만 실행하며 opt-in으로 유지한다.
- frontend는 `frontendCheck`로 검증한다.
- Python 서비스의 구현 테스트는 AI-MCP가 소유하며 [ADR-014](ADR-014-python-services-ai-mcp-ownership.md)를 따른다.
- `verifyAll`은 deterministic 범위만 묶는다.

## 결과

새 test는 의존성에 따라 tag를 선택한다. 외부 시스템 없이 기본 품질 gate를 반복 실행할 수 있다.
