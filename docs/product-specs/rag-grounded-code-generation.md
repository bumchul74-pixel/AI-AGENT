# 근거 기반 Java 코드 생성

## 결과

개발자는 자연어 요청과 생성 대상을 선택해 조직 표준에 근거한 Java 코드를 받는다.

## 요구사항

1. Controller, Service, ServiceImpl, Repository, Mapper, DTO, Domain, Exception, Test Code를 지원한다.
2. 생성 전에 표준 문서와 소스코드를 RAG 또는 승인된 MCP 검색으로 조회한다.
3. 검색된 패턴을 prompt와 결과에 반영하고 근거를 응답과 이력에 연결한다.
4. 검색 결과가 불충분하면 그 상태를 명시하며 임의 패턴을 표준으로 표현하지 않는다.
5. Provider는 설정으로 선택하고 feature 코드가 특정 Provider API에 결합되지 않게 한다.
6. 결과는 복사하거나 다운로드할 수 있다.

## 실패 조건

잘못된 생성 대상, 허용되지 않은 프로젝트 경로, RAG/LLM 오류, 파싱 불가능한 응답은 구분 가능한 오류로 반환한다. credential이나 stack trace를 반환하지 않는다.
