# 표준 문서와 소스 인덱싱

## 결과

사용자는 표준 문서와 프로젝트 소스를 업로드하고 vector 및 source graph 인덱싱 상태를 확인할 수 있다.

## 요구사항

1. 허용된 형식, 크기, 저장 경로만 처리한다.
2. document identity, project key, hash, source metadata를 유지한다.
3. Python RAG 서버가 추출, chunk, embedding, vector 저장을 담당한다.
4. Java source graph는 설정된 graph 경계를 통해 색인한다.
5. 부분 실패 시 vector/graph 상태를 독립 기록하고 재처리가 가능해야 한다.
6. 삭제된 문서는 검색과 새 생성 근거에서 제외한다.
7. 문서 목록은 프로젝트와 색인 상태를 함께 조건으로 조회할 수 있어야 한다.

업로드 데이터와 추출 원문은 일반 로그에 기록하지 않는다.
