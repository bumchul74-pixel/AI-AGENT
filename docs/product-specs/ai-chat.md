# AI 대화

## 결과

개발자는 대화 입력창에서 AI와 대화하고, `@` Auto Complete로 DB ACTIVE Agent configuration에 등록된 MCP tool을 찾아 명시적으로 사용할 수 있다.

## 요구사항

1. 메뉴명은 `AI 대화`로 표시한다.
2. 입력 커서 앞에서 `@`를 입력하면 DB ACTIVE configuration이 게시한 AgentRegistry Snapshot의 활성 tool을 표시한다.
3. `@` 뒤의 문자열로 tool 이름과 설명을 필터링한다.
4. Auto Complete는 입력창 좌측에 정렬된 컴팩트 팝업으로 최대 7개 항목을 표시한다.
5. 키보드 방향키와 Enter 또는 마우스로 tool을 선택할 수 있다.
6. 마우스 hover와 키보드 active 상태는 동일한 선택 배경색을 사용한다.
7. 선택한 tool은 실제 이름을 보존한 `@tool_name` 형식으로 메시지에 삽입한다.
8. tool 설명과 필수 인자 이름을 목록에서 확인할 수 있다.
9. 목록 로딩, 빈 목록, 연결 오류를 구분하며 오류는 전역 Toast로 알린다.
10. MCP 목록을 사용할 수 없어도 일반 채팅은 계속 입력하고 전송할 수 있다.
11. AI 응답 생성 중에는 진행 표시 상단에 요청 경과 시간을 초 단위로 표시한다.
12. 사용자 요청이 3개 이상인 대화에는 사용자 요청 기준의 대화 내비게이터를 표시한다.
13. 내비게이터 마커 선택 시 해당 사용자 요청으로 이동하고 현재 구간을 강조한다.
14. 데스크톱에서는 요청 마커와 미리보기를, 모바일에서는 이전·다음 요청과 현재 위치를 제공한다.

15. AI response generation queues additional messages and executes them in input order.
16. The request stack uses one horizontal chip row showing count, order, message, and attachment name.
17. Users can remove individual queued requests before execution.
18. Stopping the current response automatically starts the next queued request.
19. Conversation switching and resend remain locked while queued requests exist.
## 실패 조건

Agent configuration Snapshot을 조회할 수 없으면 tool 목록을 제공하지 않는다. 목록은 설정 상태이며 MCP 서버의 실시간 연결 가능성을 보장하지 않는다. 목록 오류가 발생해도 일반 채팅 입력을 차단하지 않고 전역 Toast로 원인을 알린다.

20. 명시된 여러 MCP tool은 하나의 실행 계획에 포함되며 설정된 dependency 순서를 따른다.
21. 독립 실행 단계는 설정된 최대 병렬도 안에서 동시에 실행된다.
22. Agent 답변에는 실행 ID, 단계별 capability, 상태, dependency, 시도 횟수, fallback 여부와 소요 시간을 접을 수 있는 패널로 표시한다.
23. 실행 단계 패널은 요청 원문, tool 인자·결과, 로컬 경로를 표시하지 않는다.