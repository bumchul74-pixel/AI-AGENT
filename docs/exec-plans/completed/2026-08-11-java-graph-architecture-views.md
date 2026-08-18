# Java Graph 아키텍처 보기 확장

## 목표

Java Graph 기본 화면을 패키지 컨테이너 계층으로 표현하고, 클래스 영향도·Controller→Service→DB 흐름·패키지 의존성 매트릭스와 순환 의존성 분석을 제공한다.

## 제약

- 기존 Source Graph REST 응답만 사용하고 서버 저장 상태를 변경하지 않는다.
- 패키지 탐색기, 원문 보기, 프로젝트·검색 필터를 유지한다.
- 기존 작업 트리의 무관한 변경을 보존한다.

## 비목표

- Source Graph 공개 API·ontology·Neo4j 저장 모델 변경
- 서버 측 정적 분석 규칙 추가

## 현재 상태

- 패키지 Tree는 있으나 Force Graph는 선택 패키지 중심의 원형 클래스 노드만 표시한다.
- 영향도, 계층 흐름, Dependency Matrix, 순환 의존성 보기가 없다.

## 단계

1. 클래스·패키지 관계 정규화 유틸리티를 추가한다.
2. 패키지 컨테이너 계층 그래프를 기본 보기로 적용한다.
3. 클래스 좌우 영향도와 Controller→Service→DB 흐름을 추가한다.
4. Dependency Matrix와 strongly connected component 기반 순환 탐지를 추가한다.
5. 제품 명세를 갱신하고 `frontendCheck`, `verifyAll`을 실행한다.

## 검증

- `git diff --check`
- `.\gradlew.bat frontendCheck --console=plain`
- `.\gradlew.bat verifyAll --console=plain`

## 위험

- 대형 프로젝트에서는 캔버스와 매트릭스가 복잡해질 수 있다.
- 관계가 부족하면 일부 흐름이나 순환 결과가 비어 보일 수 있다.

## Rollback

새 분석 유틸리티와 보기를 제거하고 기존 선택 패키지 Force Graph로 되돌린다.

## 결정

- 현재 응답을 클라이언트에서 결정적으로 변환한다.
- 순환 의존성은 패키지 방향 그래프의 strongly connected component로 판정한다.

## 결과

- 패키지 컨테이너 계층, 클래스 좌우 영향도, Controller→Service→DB 흐름, Dependency Matrix와 순환 탐지를 구현했다.
- 기본 계층 보기에 패키지·자식 클래스 동반 이동, 클래스 Container 내부 제한, 위치 고정과 레이아웃 초기화를 추가했다.
- 샘플 Source Graph 검증 결과: hierarchy `3/3/3`, impact `1/1`, flow `1/1/1/1`, matrix `3/3/1`.
- `.\gradlew.bat frontendCheck --console=plain`: 성공.
- `.\gradlew.bat verifyAll --console=plain`: 성공.
- in-app Browser 시각 검증은 Windows sandbox `CreateProcessWithLogonW failed: 1385`로 실행하지 못했다.
