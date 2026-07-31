# 보안 정책

## 보호 대상

- LLM/MCP/DB credential
- 업로드한 표준 문서와 소스코드
- prompt, 검색 근거, 생성 이력
- 사용자가 지정한 프로젝트와 파일 경로

## 규칙

1. secret은 환경변수나 승인된 secret store로 주입한다.
2. 구성, 예제, fixture에는 비밀이 아닌 placeholder만 둔다.
3. 업로드 확장자, 크기, 저장 경로를 검증하고 path traversal을 차단한다.
4. archive 추출 시 대상 경계, 파일 수, 압축 해제 크기를 제한한다.
5. 외부 시스템에는 요청 수행에 필요한 최소 데이터만 전달한다.
6. 로그와 오류 응답에서 credential, 개인정보, 원문 source를 제거한다.
7. 다운로드는 허용된 저장 경계 안의 결과만 제공한다.
8. 새 endpoint, 인증, destructive MCP tool에는 보안 검토와 실행 계획이 필요하다.

실제 secret을 issue나 로그에 붙이지 않는다. 관련 변경에는 경로 검증, masking, 오류 노출 test를 추가한다.
