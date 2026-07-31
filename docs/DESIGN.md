# 설계 개요

## 기본 규칙

1. 제품 동작은 `docs/product-specs`에 기록한다.
2. 의존 방향은 `ARCHITECTURE.md`를 따른다.
3. LLM, RAG, MCP, DB, graph는 교체 가능한 외부 경계로 취급한다.
4. 생성 결과보다 검색 근거의 추적 가능성을 우선한다.
5. 실패를 조용히 무시하거나 근거 없는 생성으로 대체하지 않는다.
6. 중요한 제약은 test 또는 `verifyAll`로 실행 가능하게 만든다.

## 문서 배치

- 제품 동작: [product-specs](product-specs/index.md)
- 설계 결정: [design-docs](design-docs/index.md)
- 실행 계획: [PLANS.md](PLANS.md)
- 신뢰성: [RELIABILITY.md](RELIABILITY.md)
- 보안: [SECURITY.md](SECURITY.md)
- 참조: [references](references/index.md)
- 품질 현황: [QUALITY_SCORE.md](QUALITY_SCORE.md)
