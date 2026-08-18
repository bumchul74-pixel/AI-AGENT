# Java 메서드 품질 지표

Java source 인덱싱은 JavaParser가 식별한 선언 메서드마다 다음 속성을 계산한다.

| 필드 | 정의 |
|---|---|
| `methodUid` | 프로젝트, 선언 타입 FQN, 정규화 signature로 만든 안정적 식별자 |
| `startLine`, `endLine` | 메서드 선언 AST 범위의 1-based 시작·종료 줄 |
| `lineCount` | `endLine - startLine + 1` |
| `methodBody` | 주석을 제거한 메서드 block 본문 |
| `normalizedBody` | 문자열·문자·text block 내부 공백은 보존하고 나머지 서식 공백을 제거한 본문 |
| `methodHash` | normalizedBody의 SHA-256 |
| `structuralHash` | 매개변수·지역변수 이름과 리터럴을 일반화한 본문의 SHA-256 |
| `cyclomaticComplexity` | 1 + 분기/반복/catch/switch entry/삼항식 수 + 논리 AND/OR 수 |
| `cognitiveComplexity` | 각 제어 구조의 1 + 상위 제어 구조 중첩 깊이의 합 + 논리 AND/OR 수 |
| `maxNestingDepth` | 가장 깊은 제어 구조의 1-based 중첩 깊이 |
| `parameterCount` | 선언 매개변수 수 |
| `returnCount` | return statement 수 |
| `throwCount` | throw statement 수이며 throws 선언 수가 아님 |
| `branchCount` | 분기/반복/catch/switch entry/삼항식 수 |
| `callCount` | method call expression 수 |

## 저장 계약

- Neo4j의 선언 `Method` 노드에 모든 필드를 속성으로 저장한다.
- VectorDB에는 `contentType=java-method` chunk를 추가한다.
- chunk content는 선언 타입과 signature 및 methodBody로 구성한다.
- chunk의 `entityIds`에는 methodUid, 선언 타입 UID, source file UID를 넣는다.
- chunk metadata에는 위 품질 필드와 methodUid를 유지한다.
- chunk ID는 methodUid의 SHA-256으로 안정적으로 생성하여 같은 메서드 재색인이 기존 항목을 교체하게 한다.

## 해석 규칙

- methodHash가 같으면 주석과 서식 차이를 제외한 정확 중복 후보이다.
- structuralHash가 같으면 변수명과 리터럴 차이를 제외한 구조 중복 후보이다.
- 두 hash는 중복 판정 후보를 만드는 지표이며, 짧은 접근자나 상용구 코드는 별도 임계치로 제외할 수 있다.
- 기존 인덱스에는 품질 필드가 없으므로 파일을 재색인해야 한다.
