# Java 메서드 품질 메타데이터와 Vector chunk

## 목표

JavaParser가 식별한 각 메서드에 위치, 본문, 정규화 본문, 중복 탐지 해시와 복잡도 지표를 계산하고 Neo4j Method 노드 및 VectorDB java-method chunk에 동일한 식별자로 저장한다.

## 제약

- 기존 문서 고정 길이 chunk와 source graph 계약을 유지한다.
- 품질 계산은 외부 서비스 없이 결정적으로 수행한다.
- 원문과 메서드 본문을 로그에 남기지 않는다.
- Vector chunk와 graph Method 노드는 안정적인 methodUid로 연결한다.
- 기존 업로드와 재색인은 멱등성을 유지한다.

## 비목표

- 품질 대시보드와 품질 Gate UI는 이번 변경에 포함하지 않는다.
- 중복 판정 임계치와 프로젝트별 정책 관리는 후속 기능으로 남긴다.
- 기존 인덱스 데이터 자동 migration은 수행하지 않고 재색인으로 보강한다.

## 현재 상태

- Java source는 고정 길이 문자 chunk로 VectorDB에 저장된다.
- JavaParser는 Neo4j Method 노드와 호출 관계를 만들지만 본문 hash와 복잡도는 저장하지 않는다.
- 동일 파일 업로드는 fileHash로 방지하지만 메서드 중복 데이터는 없다.

## 단계

1. JavaMethodQualityAnalyzer로 위치, 본문 정규화, SHA-256, 복잡도와 개수 지표를 계산한다.
2. JavaSourceGraphAnalyzer가 Method 노드에 품질 속성을 기록한다.
3. JavaMethodVectorChunkFactory가 Method 속성으로 java-method chunk를 만들고 methodUid를 entityIds와 metadata에 저장한다.
4. RagVectorIngestTask가 Java 파일의 메서드 chunk를 추가 저장하고 chunk 상태를 합산한다.
5. 분석기와 chunk factory 테스트, 문서 및 전체 gate를 검증한다.

## 검증

- JavaSourceGraphAnalyzerTest: 위치, 본문, hash, 복잡도, nesting, count 속성 검증
- JavaMethodVectorChunkFactoryTest: contentType, methodUid, entityIds, metadata와 안정적 chunkId 검증
- .\gradlew.bat test
- .\gradlew.bat ragTest
- .\gradlew.bat frontendCheck
- .\gradlew.bat verifyAll

## 위험

- 메서드 본문 metadata로 Vector 저장량이 증가할 수 있다.
- 단순 구조 hash는 의미적으로 다른 짧은 CRUD 메서드를 후보로 묶을 수 있으므로 확정 판정이 아닌 후보 생성에 사용한다.
- 복잡도 계산 정의가 도구마다 다를 수 있어 계산 규칙을 제품 명세에 고정해야 한다.

## Rollback

Java 메서드 chunk 생성 호출과 Method 품질 속성 추가를 되돌린 뒤 해당 sourceKey를 재색인한다. 기존 일반 문서 chunk와 graph 식별자는 유지된다.

## 결정

- exact duplicate 후보는 주석과 서식만 제거한 normalizedBody SHA-256을 사용한다.
- structural duplicate 후보는 지역 변수, 매개변수, 리터럴을 일반화한 structuralBody SHA-256을 사용한다.
- cyclomatic complexity는 기본값 1에 분기, 반복, catch, switch entry, 삼항식, 논리 AND/OR를 더한다.
- cognitive complexity는 제어 구조별 1과 중첩 깊이를 더하고 논리 AND/OR를 추가한다.
- 품질 원천 데이터는 Neo4j Method 노드, 유사도 검색 데이터는 VectorDB java-method chunk에 둔다.

## 검증 결과

- JavaSourceGraphAnalyzerTest와 JavaMethodVectorChunkFactoryTest 통과
- .\gradlew.bat test --console=plain 통과
- .\gradlew.bat verifyAll --console=plain 통과
- Python RAG unittest 16개 통과
- frontend Vite production build 통과
- 남은 gap: 품질 대시보드와 프로젝트별 임계치는 후속 기능
