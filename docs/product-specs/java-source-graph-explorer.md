# Java Source Graph 탐색

## 결과

개발자는 Java 소스 구조와 의존성을 패키지·클래스·아키텍처 계층으로 탐색하고 순환 패키지 의존성을 확인한다.

## 요구사항

1. 기본 보기는 패키지를 컨테이너로, 내부 JavaType을 자식 노드로 표현한다.
2. 기본 보기에서 패키지 Container를 끌면 내부 클래스가 함께 이동하고, 개별 클래스는 자신의 Container 경계 안에서만 이동한다.
3. 사용자는 변경한 위치를 유지하거나 레이아웃 초기화로 기본 계층 배치를 복원할 수 있다.
4. 패키지 탐색기 선택을 그래프와 소스 목록에서 식별할 수 있어야 한다.
5. JavaType 선택 시 왼쪽에 유입, 중앙에 선택 클래스, 오른쪽에 유출 의존성을 표시한다.
6. 계층 흐름은 Controller, Service, Repository/Mapper, Database 순서로 표시한다.
7. Dependency Matrix는 패키지 간 방향별 의존 건수를 표시한다.
8. 순환 의존성은 서로 도달 가능한 패키지 그룹을 경로와 함께 경고한다.
9. 모든 보기는 동일한 Source Graph 결과에서 파생하며 원본 데이터를 변경하지 않고, 관계 근거가 없으면 임의 연결을 만들지 않는다.

## 실패 조건

프로젝트 미선택, 조회 실패, 유효한 JavaType 또는 패키지 관계 부재를 구분 가능한 빈 상태나 오류로 표시한다.
11. Relationship lines start and end at each node boundary, and directional arrows terminate at the target boundary instead of crossing node content.
