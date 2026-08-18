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

## Agent capability Registry

Agent routing과 workflow 정책의 정상 운영 원본은 PostgreSQL의 agent_configuration_version이다. ACTIVE version은 하나만 존재하며 application 업무 경로는 DB를 직접 조회하지 않고 검증된 Memory Snapshot을 참조한다.

- Agent: id, name, enabled, executor, server
- Capability: id, tool, enabled, intents, argumentResolver, priority, timeoutMs, requiresApproval
- Workflow: maxParallelism, dependencies, maxAttempts, retryBackoffMs, fallbackCapabilityIds
- capability id 또는 tool 중복, 지원하지 않는 executor·server·resolver, 알 수 없는 참조, 자기 참조와 dependency cycle은 Memory 게시 전에 거부한다.
- MCP Tool의 이름과 실제 입력 schema는 MCP tools/list 응답을 기준으로 한다.
- API key, token, prompt, 요청 원문, Tool 인자·결과, 로컬 경로는 configuration JSON에 기록하지 않는다.

내부 AgentConfigurationService.saveAndActivate는 유효한 전체 문서를 DRAFT로 저장하고 기존 ACTIVE를 ARCHIVED로 바꾼 뒤 새 version을 ACTIVE로 만드는 transaction을 수행한다. commit이 완료된 후 local Memory Snapshot을 즉시 교체한다. 다른 instance 또는 외부에서 변경된 ACTIVE version은 polling refresh가 읽고 검증한 뒤 반영한다. 실패 시 기존 last-known-good Snapshot을 유지한다.

application.yml에는 다음 bootstrap 및 장애 복구 설정만 둔다.

- AGENT_CONFIGURATION_DATABASE_ENABLED: DB 원본 사용 여부. false이면 bundled bootstrap만 사용한다.
- AGENT_CONFIGURATION_SEED_ENABLED: ACTIVE version이 없는 빈 DB에 bootstrap 문서를 seed할지 결정한다.
- AGENT_CONFIGURATION_FALLBACK_ON_STARTUP: 시작 시 DB load 실패를 bootstrap으로 복구할지 결정한다.
- AGENT_CONFIGURATION_REFRESH_MODE: POLLING 또는 MANUAL.
- AGENT_CONFIGURATION_REFRESH_INTERVAL_MS: polling 간격. 최소 1000ms, 기본 30000ms.
- bootstrap-location: classpath:agent-orchestration-bootstrap.json. 배포 artifact에 포함된 초기 seed 및 장애 복구 문서다.

각 workflow는 계획 시점 Snapshot과 configuration version을 끝까지 유지하며 agent_execution_history.configuration_version에 version을 기록한다. 인증·권한·감사 경계가 정해지기 전에는 외부 configuration 쓰기 API를 제공하지 않는다.
### Agent configuration 관리 API

- AGENT_CONFIGURATION_ADMIN_API_ENABLED: 로컬 관리자 API 활성화 여부. 기본값 true이며 false이면 모든 요청을 503으로 차단한다. 활성 상태에서도 원격 요청은 403으로 거부한다.
- GET /api/admin/agent-configurations/active: 현재 Memory Snapshot의 version과 configuration 조회
- PUT /api/admin/agent-configurations/active: 전체 configuration을 새 DB version으로 저장·활성화
- POST /api/admin/agent-configurations/refresh: DB ACTIVE version을 검증하여 local Memory에 반영

AGENT_CONFIGURATION_ADMIN_API_ENABLED=false이면 API는 503으로 fail-closed 한다. true여도 localhost 요청만 허용하며, 인증 없이 동작하므로 reverse proxy나 외부망에 노출하지 않는다. application.yml의 admin-token 항목은 로그인/권한 기능 도입 전까지 주석 처리한다.

### MCP Tool 자동 동기화

- AGENT_CONFIGURATION_TOOL_SYNC_ENABLED: 무승인 자동 동기화 활성화, 기본 true
- AGENT_CONFIGURATION_TOOL_SYNC_INTERVAL_MS: 동기화 간격, 기본 30000ms
- AGENT_CONFIGURATION_TOOL_SYNC_INITIAL_DELAY_MS: 최초 실행 지연, 기본 30000ms
- 변경 감지 시 mcp-auto-sync 주체로 새 immutable version을 저장·활성화한다.
- 빈 목록 또는 실패 시 현재 last-known-good Snapshot을 유지한다.