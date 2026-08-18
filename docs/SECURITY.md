# 보안 정책

## 보호 대상

- LLM/MCP/DB credential
- 업로드한 표준 문서와 소스코드
- prompt, 검색 근거, 생성 이력
- 사용자가 지정한 프로젝트와 파일 경로
- Agent 실행 요청, Tool 인자와 실행 결과

## 규칙

1. secret은 환경변수나 승인된 secret store로 주입한다.
2. 구성, 예제, fixture에는 비밀이 아닌 placeholder만 둔다.
3. 업로드 확장자, 크기, 저장 경로를 검증하고 path traversal을 차단한다.
4. archive 추출 시 대상 경계, 파일 수, 압축 해제 크기를 제한한다.
5. 외부 시스템에는 요청 수행에 필요한 최소 데이터만 전달한다.
6. 로그와 오류 응답에서 credential, 개인정보, 원문 source를 제거한다.
7. 다운로드는 허용된 저장 경계 안의 결과만 제공한다.
8. 새 endpoint, 인증, destructive MCP tool에는 보안 검토와 실행 계획이 필요하다.
9. Agent 실행 이력에는 요청 원문, Tool 인자/결과, 로컬 경로를 저장하지 않고 요청 hash와 안전한 실행 metadata만 저장한다.
10. Agent configuration DB에는 routing·dependency·retry·fallback metadata만 저장하고 credential, prompt, 요청 원문, Tool 인자·결과, 로컬 경로를 저장하지 않는다.

실제 secret을 issue나 로그에 붙이지 않는다. 관련 변경에는 경로 검증, masking, 오류 노출 test를 추가한다.

## 메서드 품질 metadata

`methodBody`와 `normalizedBody`는 업로드한 소스코드와 동일한 보호 대상으로 취급한다. 일반 로그, 오류 응답, metric label에 본문이나 hash 원문을 기록하지 않는다. Vector chunk와 Neo4j Method 노드는 기존 sourceKey/graphKey 삭제 경계를 따르며 문서 삭제 시 함께 제거해야 한다.

소스 품질 대시보드는 메서드 본문을 일괄 반환하지 않는다. 중복 그룹 상세 조회는 사용자가 선택한 프로젝트와 중복 hash 범위로 제한하며 `normalizedBody`는 반환하지 않는다.

고복잡도 메서드 상세 조회도 사용자가 선택한 프로젝트와 `methodUid`로 범위를 제한하며 대시보드 응답에는 본문을 포함하지 않는다.

## Agent workflow 상태 노출

Agent 실행 상태 DTO에는 execution id, agent/capability id, route 종류, 안전한 tool 이름, dependency id, 상태, 시도 횟수, fallback id, 소요 시간과 오류 유형만 포함한다. 사용자 요청 원문, MCP 인자·결과, prompt, source 본문과 로컬 경로는 포함하지 않는다. tool이 아닌 route의 target은 route 종류로 치환한다.

Agent configuration 변경은 validation과 transaction을 보장하는 내부 서비스 경계를 통과한다. 직접 DB 수정은 검증 우회로 간주하며, 외부 쓰기 endpoint는 인증·권한·감사 정책과 운영 주체가 정해진 뒤 별도 보안 검토를 거쳐 추가한다.
## Agent configuration 관리 API 임시 경계

로그인 기능이 없는 현재 단계에서는 관리자 token 인증을 사용하지 않는다. 화면을 로컬에서 바로 사용할 수 있도록 AGENT_CONFIGURATION_ADMIN_API_ENABLED=true가 기본값이며 false일 때 모든 관리 요청을 503으로 거부한다. 활성 상태에서도 HTTP remote address가 loopback이 아닌 요청은 403으로 거부한다. reverse proxy가 localhost에서 요청을 전달하면 이 경계가 무력화될 수 있으므로 로그인 도입 전에는 관리 API를 proxy 또는 외부망에 노출하지 않는다.

application.yml의 admin-token 항목은 주석 처리한다. 로그인과 권한 기능이 도입되면 SSO/RBAC 기반 인증·인가, 사용자별 감사와 CSRF/CORS 정책을 함께 추가해야 한다. 관리 API는 기존 version 삭제나 직접 update를 제공하지 않고 검증된 새 version 활성화만 허용한다.

## Neo4j explorer exposure

The generic Neo4j explorer is read-only and does not accept arbitrary Cypher. Node identifiers are bound query parameters. Node and relationship property keys containing password, secret, token, credential, apiKey, or api_key are masked before serialization. This masking is defense in depth; credentials must not be stored as graph properties.
