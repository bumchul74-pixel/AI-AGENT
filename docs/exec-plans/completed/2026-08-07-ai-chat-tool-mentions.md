# AI 대화 MCP tool Auto Complete

## 목표

AI 질의 메뉴를 AI 대화로 변경하고, 채팅 입력창에서 `@`로 실제 MCP tool 목록을 검색·선택해 메시지에 삽입할 수 있게 한다.

## 제약

- 프런트엔드는 기존 AI-MCP REST 계약만 사용하며 tool 목록을 하드코딩하지 않는다.
- 선택한 표기는 현재 채팅 MCP 라우터가 인식하는 실제 tool 이름을 유지한다.
- MCP 연결 실패는 전역 Toast로 알리고 입력과 일반 채팅은 계속 사용할 수 있게 한다.
- 키보드, 마우스, 모바일 입력을 지원하고 접근성 역할을 제공한다.

## 비목표

- tool을 자동 실행하거나 자동 선택하지 않는다.
- tool 인자 입력 폼을 새로 만들지 않는다.
- MCP 서버 또는 transport 계약을 변경하지 않는다.

## 시작 상태

- `/api/mcp/ai-mcp/tools`가 `tools/list` 결과를 반환한다.
- 채팅 MCP 라우터는 메시지 안의 실제 tool 이름을 명시적 호출로 인식하며 `@tool_name`도 유효하다.
- 채팅 입력창에는 tool 검색이나 선택 UI가 없었다.

## 구현 단계

1. AI 대화 사용자 동작을 제품 명세에 기록한다.
2. MCP tool 목록 조회 API 함수를 추가한다.
3. `@` query 감지, 목록 필터, 선택 삽입, 키보드 탐색 UI를 구현한다.
4. 메뉴명과 안내 문구, 반응형 스타일을 갱신한다.
5. 프런트엔드 build와 `verifyAll`을 실행한다.
6. 목록을 컴팩트 Auto Complete로 조정하고 hover와 keyboard active 스타일을 통일한다.

## 검증 기준

- `@` 입력 시 실제 MCP tool 목록이 표시된다.
- 이름 일부를 입력하면 목록이 필터링된다.
- 방향키와 Enter 또는 마우스로 선택하면 `@tool_name`이 현재 커서 위치에 삽입된다.
- Esc로 목록을 닫고 일반 Enter는 메시지를 전송한다.
- 키보드 선택 항목은 스크롤 영역 안에 유지된다.
- MCP가 비활성 또는 연결 실패여도 Toast 이후 일반 채팅 입력은 유지된다.
- `frontendCheck`, `verifyAll`

## 위험

- MCP 서버가 꺼져 있으면 목록을 조회할 수 없다.
- tool의 필수 인자는 여전히 대화 본문에 `key=value`로 작성해야 한다.

## 롤백

메뉴 상수, MCP 목록 API 함수, tool Auto Complete 입력 컴포넌트와 스타일, 관련 문서를 되돌린다.

## 결정

- tool 목록의 단일 출처는 MCP `tools/list` 응답으로 한다.
- 선택 표기는 사용자가 의도를 명시적으로 확인할 수 있도록 `@tool_name`으로 유지한다.
- 첫 `@` 입력 시 지연 조회하고 같은 화면 세션에서는 결과를 재사용한다.
- Selectbox 대신 입력 맥락을 유지하는 `combobox` 기반 Auto Complete를 사용한다.

## 결과

- 메뉴명과 설명을 AI 대화 중심으로 변경했다.
- 실제 MCP tool 목록을 이름과 설명으로 필터링하고 최대 7개까지 표시한다.
- 입력창 좌측 정렬, 컴팩트 행, 통일된 hover와 keyboard active 배경색을 적용했다.
- 키보드와 마우스로 선택한 `@tool_name`을 현재 커서 위치에 삽입한다.
- 필수 인자 이름, 로딩, 빈 목록, 연결 오류를 처리하며 일반 채팅을 차단하지 않는다.

## 검증 결과

- `AiMcpChatContextProviderTest`: 성공
- `.\gradlew.bat verifyAll --console=plain`: 성공 (Java, integration, RAG 16 tests, frontend build, harness docs)
