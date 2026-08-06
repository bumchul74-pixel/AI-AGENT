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

## Frontend backend request timeout

Frontend에서 Backend로 보내는 모든 HTTP 요청은 `frontend/src/api/apiClient.js`의 공통 요청 함수를 사용한다.

- 설정 파일: `frontend/src/config/application.properties`
- 속성: `backend.request.timeout-ms`
- 기본값: `60000`ms(1분)

설정 시간을 초과하면 브라우저 요청을 중단하고 HTTP 408 성격의 요청 오류로 처리한다.
사용자에게는 전역 Toast UI로 타임아웃 메시지를 표시하며 화면 내부에 같은 오류를 중복 노출하지 않는다.
AI 질의 응답 대기 중에는 전송 버튼을 중지 버튼으로 전환하고, 사용자가 중지하면 진행 중인 브라우저 요청을 즉시 취소한다.
