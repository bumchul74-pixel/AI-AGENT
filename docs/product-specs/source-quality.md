# Java 소스 품질

## 결과

개발자는 프로젝트별 Java 메서드 중복과 복잡도를 확인하고, 시간에 따른 품질 변화와 설정한 Quality Gate 통과 여부를 판단한다.

## 요구사항

1. 품질 분석은 JavaParser가 Neo4j Method 노드에 저장한 결정적 품질 metadata를 사용한다.
2. 대시보드 API 응답에는 `methodBody`와 `normalizedBody`를 포함하지 않는다. 사용자가 중복 그룹을 명시적으로 선택한 경우에만 프로젝트 범위의 상세 API가 해당 그룹의 `methodBody`를 반환한다.
3. exact 중복은 `methodHash`, structural 중복은 `structuralHash`가 같은 메서드를 그룹화한다.
4. 최소 중복 라인보다 짧은 메서드는 중복 그룹과 비율에서 제외한다.
5. 순환 또는 인지 복잡도가 프로젝트 임계치를 초과한 메서드를 높은 값부터 제공한다.
6. 기본 임계치는 순환 복잡도 10, 인지 복잡도 15, 중복 비율 10%, 최소 중복 라인 5이다.
7. 임계치 초과 메서드가 하나 이상이거나 중복 비율이 임계치를 초과하면 Gate는 `FAIL`, 아니면 `PASS`이다.
8. 품질 평가는 현재 요약이 직전 값과 다를 때만 프로젝트 snapshot을 추가한다.
9. 화면은 프로젝트 선택, 요약, Gate 사유, 임계치 편집, 중복 그룹, 고복잡도 순위와 최근 30개 추이를 제공한다.
10. Neo4j가 비활성화되었거나 재색인된 Method가 없으면 빈 분석 결과를 명시한다.

11. The quality screen provides confirmed project-wide Java reindexing, visible progress, partial-failure totals, and an automatic quality refresh after completion.
12. Selecting a duplicate group opens a comparison view containing every matched method body and its source location.
13. Quality evaluation success, warning, partial-failure, and error feedback uses the global Toast UI and is not duplicated in an inline message area.
14. Selecting a high-complexity method opens its indexed method body, source location, and complexity metrics.
15. Quality Gate `FAIL`은 평가 요청 실패가 아닌 정상 평가 결과이다. 화면은 평가 완료를 성공으로 안내하고, `PASS`/`FAIL` 상태와 임계치 초과 사유를 본문 결과 영역에 지속적으로 표시한다. 오류 Toast는 API, 네트워크 또는 서버 처리 실패에만 사용한다.

## API

- `GET /api/source-quality/projects/{projectKey}`: 현재 분석과 저장된 추이를 조회한다.
- `POST /api/source-quality/projects/{projectKey}/evaluate`: 현재 분석을 평가하고 변경된 snapshot을 저장한다.
- PUT /api/source-quality/projects/{projectKey}/thresholds: 프로젝트 임계치를 저장하고 Gate를 다시 평가한다.
- GET /api/source-quality/projects/{projectKey}/duplicate-groups/{type}/{hash}: 선택한 중복 그룹의 메서드 본문을 조회한다.
- `GET /api/source-quality/projects/{projectKey}/methods/detail?methodUid={methodUid}`: 선택한 고복잡도 메서드의 본문과 품질 지표를 조회한다.

## 실패 조건

존재하지 않는 프로젝트, 범위를 벗어난 임계치, Neo4j 또는 PostgreSQL 조회 실패를 정상 결과로 숨기지 않는다.
