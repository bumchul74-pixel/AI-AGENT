## Project Overview

이 프로젝트는 Java 프로젝트 개발 생산성 향상을 위한 RAG 기반 챗봇 웹 서비스이다.

개발자가 특정 템플릿 생성을 요청하면, 시스템은 RAG를 통해 도메인에서 사용 중인 표준 문서와 표준 소스코드를 검색하고, 검색 결과를 기반으로 Controller, Service, Repository, DTO, Mapper 등을 생성하여 제공한다.

---

## LLM Configuration

LLM Provider는 설정 파일을 통해 변경 가능하도록 구현한다.

지원 대상 예시

* OpenAI

LLM 관련 설정은 application.yml 에서 관리한다.

예시:

llm:
provider: openai

openai:
api-key: ${OPENAI_API_KEY}
model: gpt-5

---

## Source Generation Rules

생성 가능한 대상

* Controller
* Service
* ServiceImpl
* Repository
* Mapper
* DTO
* DOMAIN
* Exception
* Test Code

생성 시 반드시 RAG 검색 결과를 참고한다.

LLM이 임의의 패턴을 생성하지 않는다.

검색된 표준 코드가 존재하면 해당 패턴을 우선 적용한다.

---

## Configuration Policy

민감정보는 소스코드에 하드코딩하지 않는다.

API Key는 application.yml 또는 환경변수로 관리한다.

예시

OPENAI_API_KEY
LLM Provider 변경 시 소스 수정 없이 application.yml 설정만 변경 가능해야 한다.
Strategy Pattern 기반으로 Provider를 분리하여 구현한다.
---

## Frontend Overview


React.js 화면은 유지보수가 쉽고 초보자도 구조를 빠르게 이해할 수 있도록 단순한 계층 구조로 구성한다.

Frontend는 복잡한 아키텍처보다 명확한 역할 분리를 우선한다.

권장 구조:
frontend
├── src
│   ├── api
│   │   ├── chatApi.js
│   │   ├── documentApi.js
│   │   └── generateApi.js
│   │
│   ├── components
│   │   ├── common
│   │   │   ├── Button.jsx
│   │   │   ├── Input.jsx
│   │   │   ├── Modal.jsx
│   │   │   └── Loading.jsx
│   │   │
│   │   ├── chat
│   │   │   ├── ChatInput.jsx
│   │   │   ├── ChatMessage.jsx
│   │   │   └── ChatResult.jsx
│   │   │
│   │   └── layout
│   │       ├── Header.jsx
│   │       ├── Sidebar.jsx
│   │       └── MainLayout.jsx
│   │
│   ├── pages
│   │   ├── ChatPage.jsx
│   │   ├── TemplateGeneratePage.jsx
│   │   ├── DocumentManagePage.jsx
│   │   └── HistoryPage.jsx
│   │
│   ├── hooks
│   │   ├── useChat.js
│   │   ├── useDocument.js
│   │   └── useGenerate.js
│   │
│   ├── store
│   │   └── appStore.js
│   │
│   ├── utils
│   │   ├── dateUtils.js
│   │   └── fileUtils.js
│   │
│   ├── constants
│   │   └── apiConstants.js
│   │
│   ├── routes
│   │   └── AppRoutes.jsx
│   │
│   ├── App.jsx
│   └── main.jsx
│
├── package.json
└── vite.config.js

주요 역할은 다음과 같다.

- 개발자의 자연어 요청 입력
- 생성 대상 템플릿 선택
- RAG 검색 결과 확인
- 생성된 Java 코드 확인
- 생성 결과 복사 또는 다운로드
- Backend REST API 호출

주요 화면 예시:

- 챗봇 질의 화면
- 템플릿 생성 요청 화면
- 생성 결과 미리보기 화면
- 문서/표준 코드 업로드 화면
- 생성 이력 조회 화면

Frontend는 비즈니스 로직을 직접 처리하지 않고, Backend API를 호출하여 결과를 표시하는 역할만 수행한다.

---

## Backend Overview

Backend는 현재 설치되어 있는 Spring Boot 기반으로 구현한다.

주요 역할은 다음과 같다.

- Frontend 요청 처리
- 코드 생성 요청 처리
- LLM API 연계
- 코드 생성 프롬프트 구성
- Controller, Service, Repository, DTO, Mapper 생성
- MyBatis 기반 DB 연계
- 생성 이력 저장
- 설정 정보 관리
- Python 기반 RAG 서버 연계

Backend에서는 RAG 기능을 직접 구현하지 않고, 별도의 Python RAG 서버와 REST API 방식으로 연계한다.

---

## Python RAG Server Overview

RAG 기능은 Python 기반의 별도 서버로 구성한다.

Python RAG 서버의 주요 역할은 다음과 같다.

- 표준 문서 업로드
- 표준 소스코드 업로드
- 텍스트 추출
- Chunk 분할
- Embedding 생성
- Vector DB 저장
- 사용자 요청 기반 유사 문서 검색
- 사용자 요청 기반 유사 소스코드 검색
- 검색 결과를 Backend에 반환

Python RAG 서버는 FastAPI 기반으로 구현하는 것을 권장한다.

```text
Spring Boot Backend
  ↓ REST API
Python RAG Server
  ↓
Embedding Model
  ↓
Vector DB


```

## Canonical Documentation

코드 변경 전 `ARCHITECTURE.md`, 관련 `docs/product-specs`, `docs/DESIGN.md`, 관련 ADR, `docs/RELIABILITY.md`, `docs/SECURITY.md`, 관련 `docs/references` 순서로 확인한다. 코드와 문서가 충돌하면 승인된 제품 명세와 아키텍처 결정을 우선한다.

## Harness Workflow

1. 관련 문서, 구성, 소스, 테스트를 읽는다.
2. 목표, 제약, 비목표, 검증 증거를 정한다.
3. `docs/PLANS.md` 기준에 해당하면 `docs/exec-plans/active`에 실행 계획을 만든다.
4. 기존 경계와 확장 지점을 사용하고 지속적인 설계 변경은 ADR로 기록한다.
5. 제품, 신뢰성, 보안, 참조 문서의 영향을 같은 변경에 반영한다.
6. 기본 gate인 `verifyAll`을 실행한다. 실제 외부 LLM, MCP, DB, Neo4j, RAG 호출은 허가된 경우에만 `liveTest`로 검증한다.
7. 완료된 계획은 `docs/exec-plans/completed`로 옮기고 수용한 gap은 기술부채 추적기에 기록한다.

## Component Boundaries

### Spring Boot Backend

- Controller는 HTTP 검증과 응답 매핑만 담당한다.
- Service는 유스케이스와 트랜잭션 흐름을 담당한다.
- Repository와 Mapper는 영속성 접근을 담당하며 SQL은 MyBatis XML에 둔다.
- LLM, RAG, MCP, graph DB는 인터페이스와 설정 경계를 통해 연결한다.
- Provider 교체가 feature service 변경으로 이어지지 않게 한다.

### React/Vite Frontend

- `api`, `components`, `pages`, `hooks`, `store`, `utils`, `constants`, `routes` 역할 분리를 유지한다.
- 생성 판단, RAG ranking, DB 규칙을 프런트엔드에 중복 구현하지 않는다.
- API의 loading, error, empty state를 명시적으로 처리한다.

### Python RAG Server

- 추출, chunk, embedding, vector 저장, 검색은 `rag-server`가 담당한다.
- Spring Boot는 Python 내부 저장소 구현이 아닌 REST 계약에만 의존한다.
- 검색 결과에는 생성 근거를 식별할 source/chunk metadata를 유지한다.

## Generation Invariants

- 생성 전에 반드시 표준 문서 또는 표준 소스코드를 RAG/MCP로 검색한다.
- 검색된 구조, 명명, 예외 처리, mapping 패턴을 우선 적용한다.
- 근거가 없거나 불충분하면 임의 패턴을 표준인 것처럼 생성하지 않고 부족한 근거를 명시한다.
- 생성 결과와 사용한 검색 근거를 추적 가능하게 유지한다.
- 부분 예제가 아니라 컴파일 가능하고 테스트 가능한 구현을 제공한다.

## Security and Configuration

- LLM Provider는 Strategy로 분리하고 `application.yml` 설정으로 선택한다.
- API key, token, password, 실제 endpoint credential을 하드코딩하지 않는다.
- 환경변수 또는 승인된 secret store를 사용한다.
- 로그, 오류 응답, 생성 이력에 credential, 개인정보, 전체 prompt 원문을 남기지 않는다.
- deterministic test에서는 외부 연동을 비활성화하거나 mock/fake로 대체한다.

## Standard Validation Commands

```powershell
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat ragTest
.\gradlew.bat frontendCheck
.\gradlew.bat verifyAll
```

- `test`: 외부 서비스 없는 Java 테스트
- `integrationTest`: `integration` tag Spring/local integration test
- `ragTest`: Python RAG unittest
- `frontendCheck`: React/Vite production build
- `validateHarnessDocs`: 필수 harness 문서와 로컬 Markdown 링크 검증
- `doctor`: Java 21과 Wrapper/lockfile 검증
- `verifyAll`: deterministic 기본 gate
- `liveTest`: 기본 gate에서 제외된 외부 시스템 검증

전체 검증을 실행하지 못하면 가능한 가장 큰 범위를 실행하고 생략한 명령과 이유를 보고한다. build 결과, runtime log, upload 데이터, credential, 무관한 변경을 포함하지 않는다.

## Frontend Feedback Policy

- UI 작업의 처리 성공, 부분 실패, 오류 메시지는 모두 동일한 전역 Toast UI로 노출한다.
- 성공, 경고, 오류는 Toast의 위치와 닫기 방식은 통일하고 상태별 색상과 아이콘으로 구분한다.
- 같은 메시지를 화면 내부 인라인 영역과 Toast에 중복 노출하지 않는다.
