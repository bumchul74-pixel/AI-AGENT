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


