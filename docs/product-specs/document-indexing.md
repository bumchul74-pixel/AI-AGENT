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

## Java 메서드 품질 인덱싱

8. Java 파일은 일반 원문 chunk와 별도로 메서드 단위 `java-method` chunk를 저장해야 한다.
9. Method graph 노드와 java-method chunk는 동일한 안정적 `methodUid`로 연결해야 한다.
10. 메서드 위치, 본문, 정규화 본문, 중복 탐지 hash, 복잡도와 구조 개수 지표를 결정적으로 계산해야 한다.
11. 지표 정의와 저장 계약은 [Java 메서드 품질 지표](../references/java-method-quality-metrics.md)를 따른다.
12. 기존 인덱스는 재색인 후 품질 metadata를 제공한다.
13. Requirement 8 is superseded for Java files: store only `java-method` chunks and do not store generic fixed-size chunks.

업로드 데이터와 추출 원문은 일반 로그에 기록하지 않는다.
