# ADR-001: 다중 런타임 경계

- 상태: Accepted
- 날짜: 2026-07-31

## 상황

AI-AGENT는 Spring Boot, React/Vite, Python RAG와 여러 외부 시스템으로 구성된다. 검색 구현과 생성 workflow가 결합되면 독립 변경과 검증이 어렵다.

## 결정

Spring Boot가 workflow와 orchestration, React가 REST client, Python이 RAG 구현을 소유한다. Spring Boot와 Python은 REST 계약으로 연결한다. LLM, MCP, graph, DB는 전용 경계와 설정으로 연결한다.

## 결과

각 런타임을 독립 test할 수 있다. 계약 변경에는 양쪽 검증이 필요하며 frontend 또는 backend에 Python 검색 구현을 복제할 수 없다.
