# 구성과 환경변수

Canonical application configuration은 `src/main/resources/application.yml`이며 실제 값은 환경변수로 덮어쓴다.

주요 구성 영역:

- LLM Provider와 endpoint/API key
- Python RAG base URL과 search path
- PostgreSQL datasource
- Neo4j/source graph 활성화
- MCP client 연결
- upload/document storage 경로
- secure coding worker

credential을 Markdown, `.env`, `application.yml`에 commit하지 않는다. 새 Provider는 속성, validation, default behavior, 환경변수 예시를 함께 문서화한다.
