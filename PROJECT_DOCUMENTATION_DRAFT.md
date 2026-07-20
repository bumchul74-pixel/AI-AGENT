# AI-AGENT 프로젝트 문서 초안

> 문서 상태: Draft 0.1  
> 기준일: 2026-07-15  
> 대상 독자: 신규 참여 개발자, Backend/Frontend/RAG 개발자, 운영 담당자

## 1. 프로젝트 개요

AI-AGENT는 사내 표준 문서와 표준 Java 소스코드를 검색해 개발자의 요청에 맞는 Spring Boot 소스를 생성하는 RAG 기반 개발 지원 웹 서비스다.

단순히 LLM에 코드 생성을 요청하지 않고 다음 정보를 조합한다.

- RAG 서버가 검색한 표준 문서 및 표준 소스
- 선택한 대상 프로젝트의 패키지와 디렉터리 구조
- MCP를 통해 조회한 프로젝트 및 데이터베이스 스키마 정보
- Java 소스의 의존 관계를 저장한 Neo4j 그래프

검색 결과가 없는 경우 코드 생성을 중단하며, 검색된 표준 패턴을 생성 프롬프트에 우선 반영한다. 생성 결과와 사용한 RAG 문서는 PostgreSQL에 이력으로 저장된다.

## 2. 주요 기능

| 구분 | 기능 |
|---|---|
| 코드 생성 | Controller, Service, ServiceImpl, Repository, Mapper, DTO, DOMAIN, Exception, Test Code 중 선택한 항목 생성 |
| 표준 자료 관리 | 문서 및 Java 소스 업로드, 목록/다운로드/삭제, 재색인 |
| RAG 검색 | 사용자 질의와 유사한 표준 자료를 검색하고 생성 컨텍스트로 제공 |
| 프로젝트 분석 | 선택한 로컬 프로젝트의 구조를 분석해 패키지와 파일 위치 결정에 활용 |
| DB 스키마 연계 | MCP DB 도구로 테이블/컬럼 정보를 조회하고 Mapper 등의 생성 정확도 보완 |
| 소스 그래프 | Java 타입과 의존 관계를 Neo4j에 색인하고 의존/영향 범위 조회 |
| 생성 이력 | 요청, 대상 유형, RAG 검색 결과, 생성 코드, LLM Provider, 그래프 색인 상태 저장 및 조회 |
| LLM Provider 교체 | 설정만으로 OpenAI 또는 Gemini 선택 |

현재 Frontend 라우트는 `TemplateGeneratePage` 한 화면에 고정되어 있다. Chat, 문서 관리, 이력, RAG 검색, Java 그래프 페이지 컴포넌트는 존재하지만 통합 라우팅은 추가 정리가 필요하다.

## 3. 시스템 구성

```text
Developer Browser
       |
       v
React + Vite (:5173)
       |
       | /api proxy
       v
Spring Boot Backend (:8081)
       |-------------------- PostgreSQL (:5432)
       |                     - 문서 메타데이터
       |                     - 코드 생성 이력
       |
       |-------------------- Neo4j (:7687)
       |                     - Java 소스/생성 코드 의존 그래프
       |
       |-------------------- Python FastAPI RAG (:8000)
       |                     - 문서 추출, Chunk 분할
       |                     - 로컬 벡터 저장 및 유사도 검색
       |
       |-------------------- MCP Server (:8092 또는 stdio)
       |                     - 프로젝트 구조 및 DB 스키마 컨텍스트
       |
       `-------------------- LLM API
                             - OpenAI 또는 Gemini
```

### 구성 요소별 책임

#### Frontend

- 개발자 요청과 생성 대상 입력
- Backend REST API 호출
- RAG 검색 결과 및 생성 코드 표시
- 표준 문서 관리와 생성 이력/그래프 조회 UI 제공
- 비즈니스 로직은 처리하지 않음

#### Spring Boot Backend

- Frontend API 제공 및 전체 생성 흐름 조정
- RAG 서버, LLM, MCP, PostgreSQL, Neo4j 연계
- 업로드 파일과 메타데이터 관리
- 생성 요청 검증, Mapper 컬럼 검증, 생성 이력 저장
- Java 소스 분석과 그래프 색인

#### Python RAG Server

- 업로드 파일에서 텍스트 추출
- 텍스트 Chunk 분할과 색인
- 질의 벡터와 저장 Chunk 간 코사인 유사도 검색
- 감시 디렉터리에 투입된 파일의 자동 수집

현재 구현은 `rag-server/data/vector_store.json`에 512차원 해시 기반 임베딩을 저장하는 `LocalVectorStore`를 사용한다. `requirements.txt`에는 Qdrant와 모델 기반 임베딩 라이브러리가 포함되어 있지만, 현재 실행 경로에는 아직 연결되어 있지 않다.

## 4. 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4.0.6, Spring AI 2.0.0, Gradle Kotlin DSL |
| DB 연계 | PostgreSQL, MyBatis 4.0.0 |
| Source Graph | Neo4j, JavaParser 3.26.4 |
| MCP | Spring AI MCP Client, Streamable HTTP, stdio filesystem server |
| Frontend | React 19, Vite 7, JavaScript, react-force-graph-2d |
| RAG Server | Python, FastAPI, Uvicorn |
| Test | JUnit 5/Spring Boot Test, pytest |

## 5. 저장소 구조

```text
AI-AGENT/
├── src/main/java/com/hanwha/ai/
│   ├── chat/          # 일반 챗봇 API
│   ├── document/      # 문서 저장, 색인 워크플로, 다운로드/삭제
│   ├── generation/    # RAG 기반 코드 생성 및 이력
│   ├── llm/           # LLM Provider 전략과 OpenAI/Gemini Client
│   ├── mcp/           # MCP 라우팅, Gateway, 컨텍스트 Provider
│   ├── rag/           # Python RAG 서버 Client/API
│   ├── sourcegraph/   # Java 분석 및 Neo4j 그래프
│   └── global/        # 공통 설정과 예외 처리
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql
│   └── mapper/        # MyBatis XML Mapper
├── frontend/          # React/Vite 애플리케이션
├── rag-server/        # FastAPI RAG 서버
├── uploads/           # 업로드 파일 저장 경로(기본값)
├── logs/              # 애플리케이션 로그
└── build.gradle.kts
```

## 6. 로컬 개발 환경

### 사전 준비

- JDK 21
- Node.js: Vite 7을 지원하는 버전 사용 권장
- Python 3 및 `venv`
- PostgreSQL
- Neo4j
- MCP 사용 시 Node.js/npm의 `npx`와 별도 AI MCP 서버
- 선택한 LLM Provider의 API Key

### 6.1 PostgreSQL 및 Neo4j 준비

기본 연결값은 다음과 같다.

| 항목 | 기본값 |
|---|---|
| PostgreSQL URL | `jdbc:postgresql://localhost:5432/mcp` |
| PostgreSQL 사용자 | `mcp` |
| PostgreSQL 비밀번호 | `mcp` |
| Neo4j URI | `bolt://localhost:7687` |
| Neo4j 사용자 | `neo4j` |
| Neo4j 비밀번호 | `neo4j_password` |

Spring Boot 시작 시 `schema.sql`이 실행되어 `rag_document`, `generation_history` 테이블과 인덱스를 생성/보정한다.

### 6.2 RAG 서버 실행

저장소 루트에서 실행한다.

```powershell
.\start-rag-server.ps1
```

기본 주소는 `http://localhost:8000`이다. 최초 실행 시 가상환경과 의존성을 설치한다. 설치를 생략하거나 서버를 재시작하려면 다음 옵션을 사용할 수 있다.

```powershell
.\start-rag-server.ps1 -SkipInstall
.\start-rag-server.ps1 -Restart
```

상태 확인:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

### 6.3 Backend 실행

LLM Provider에 맞는 환경변수를 설정한 뒤 실행한다.

```powershell
$env:LLM_PROVIDER="gemini"
$env:GEMINI_API_KEY="<your-api-key>"
.\gradlew.bat bootRun
```

OpenAI를 사용할 경우:

```powershell
$env:LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="<your-api-key>"
.\gradlew.bat bootRun
```

Backend 기본 주소는 `http://localhost:8081`이다.

### 6.4 Frontend 실행

```powershell
cd frontend
npm install
npm run dev
```

브라우저에서 `http://localhost:5173`에 접속한다. Vite는 `/api` 요청을 `http://localhost:8081`로 프록시한다.

## 7. 주요 환경변수

| 환경변수 | 용도 | 기본값 |
|---|---|---|
| `LLM_PROVIDER` | LLM Provider 선택 | `gemini` |
| `OPENAI_API_KEY` | OpenAI 인증키 | 없음 |
| `GEMINI_API_KEY` | Gemini 인증키 | 없음 |
| `POSTGRES_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/mcp` |
| `POSTGRES_USERNAME` | PostgreSQL 사용자 | `mcp` |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 | `mcp` |
| `NEO4J_URI` | Neo4j Bolt URI | `bolt://localhost:7687` |
| `NEO4J_USERNAME` | Neo4j 사용자 | `neo4j` |
| `NEO4J_PASSWORD` | Neo4j 비밀번호 | `neo4j_password` |
| `RAG_SERVER_BASE_URL` | Python RAG 서버 주소 | `http://localhost:8000` |
| `RAG_SEARCH_TOP_K` | 검색 결과 수 | `5` |
| `DOCUMENT_STORAGE_DIR` | 업로드 파일 저장 경로 | `uploads/documents` |
| `SOURCE_GRAPH_ENABLED` | Neo4j 소스 그래프 사용 여부 | `true` |
| `MCP_CLIENT_ENABLED` | MCP Client 사용 여부 | `true` |
| `MCP_CLIENT_INITIALIZED` | MCP 초기화 여부 | `false` |
| `AI_MCP_BASE_URL` | HTTP MCP 서버 주소 | `http://localhost:8092` |
| `MCP_FILESYSTEM_ROOT` | filesystem MCP 접근 루트 | 현재 저장소의 로컬 절대 경로 |
| `RAG_WATCH_ENABLED` | RAG 투입 디렉터리 감시 여부 | `true` |
| `RAG_WATCH_DIR` | 자동 수집 디렉터리 | `rag-server/inbox` |

API Key와 운영 DB 비밀번호는 소스나 공유 문서에 기록하지 않고 환경변수 또는 별도의 비밀 관리 도구로 주입한다.

## 8. 핵심 처리 흐름

### 8.1 표준 문서 등록

1. 사용자가 Backend의 문서 업로드 API에 파일을 전송한다.
2. Backend가 파일을 저장하고 PostgreSQL에 메타데이터를 기록한다.
3. 문서 색인 워크플로가 RAG 서버에 텍스트/파일을 전달한다.
4. Java 파일이면 Neo4j 소스 그래프 색인도 수행한다.
5. 벡터 색인과 그래프 색인 결과를 각각 문서 상태에 반영한다.

문서 유형은 `STANDARD_DOCUMENT`, `STANDARD_SOURCE`를 지원한다. 유형을 생략하면 `.java` 파일은 표준 소스, 나머지는 표준 문서로 판정한다.

### 8.2 코드 생성

1. 사용자가 생성 대상, 요구사항, 대상 프로젝트 경로를 선택한다.
2. Backend가 입력값과 선택된 생성 대상을 검증한다.
3. MCP 또는 로컬 분석기로 대상 프로젝트 구조를 분석한다.
4. 필요 시 MCP DB 도구로 관련 테이블과 컬럼을 조회한다.
5. Python RAG 서버에서 표준 코드/문서를 검색한다.
6. 검색 결과가 없으면 생성을 중단한다.
7. 프로젝트 구조, DB 스키마, RAG 결과를 조합해 LLM에 전달한다.
8. Mapper 생성 시 실제 DB 컬럼과 결과를 검증하고, 불일치하면 한 차례 재생성을 시도한다.
9. 생성 결과와 컨텍스트를 PostgreSQL에 저장한다.
10. 생성 Java 코드를 Neo4j 그래프로 색인한다.

## 9. Backend API 요약

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/chat` | 일반 챗 질의 |
| `POST` | `/api/generations` | RAG 기반 코드 생성 |
| `GET` | `/api/generations/project-structures` | 생성 대상 프로젝트 목록 |
| `GET` | `/api/generations/history` | 생성 이력 검색 |
| `GET` | `/api/generations/history/{id}` | 생성 이력 상세 |
| `GET` | `/api/generations/history/{id}/graph` | 이력의 소스 그래프 조회 |
| `POST` | `/api/generations/history/{id}/graph/reindex` | 이력 그래프 재색인 |
| `GET` | `/api/documents` | 문서 전체 목록 |
| `GET` | `/api/documents/page` | 문서 페이지 조회 |
| `POST` | `/api/documents` | 문서 업로드(multipart) |
| `POST` | `/api/documents/{id}/reindex` | 문서 재색인 |
| `GET` | `/api/documents/{id}/download` | 원본 다운로드 |
| `DELETE` | `/api/documents/{id}` | 문서 삭제 |
| `POST` | `/api/rag/search` | Backend 경유 RAG 검색 |
| `GET` | `/api/rag/stats` | 색인된 Java 파일 통계 |
| `GET` | `/api/source-graph` | 소스 그래프 개요/검색 |
| `GET` | `/api/source-graph/node-source` | 그래프 노드 원문 조회 |
| `GET` | `/api/source-graph/dependencies` | 특정 타입의 의존 대상 조회 |
| `GET` | `/api/source-graph/impacts` | 특정 타입 변경의 영향 범위 조회 |
| `POST` | `/api/source-graph/java-files` | Java 소스 그래프 색인 |
| `GET/POST` | `/api/mcp/ai-mcp/**` | MCP 도구, 리소스, 프롬프트 Gateway |

코드 생성 요청 예시:

```http
POST /api/generations
Content-Type: application/json

{
  "targetTypes": ["Controller", "Service", "DTO"],
  "prompt": "회원 목록 조회 API를 생성해줘",
  "projectStructure": "D:\\workspace\\management"
}
```

RAG 검색 요청 예시:

```http
POST /api/rag/search
Content-Type: application/json

{
  "query": "회원 조회 Controller 표준 패턴",
  "topK": 5
}
```

## 10. 테스트 및 빌드

Backend 테스트:

```powershell
.\gradlew.bat test
```

Frontend 빌드:

```powershell
cd frontend
npm run build
```

RAG 서버 테스트:

```powershell
cd rag-server
.\.venv\Scripts\Activate.ps1
pytest
```

변경 전후 최소 확인 항목은 Backend 테스트, Frontend 빌드, RAG `/health`, 주요 생성 요청 1건이다.

## 11. 개발 규칙

- 생성 기능은 반드시 RAG 검색 결과를 사용한다.
- 표준 코드가 검색되면 해당 패턴을 우선한다.
- LLM Provider별 구현은 `LlmClient` 전략 뒤에 분리하고 설정으로 교체 가능하게 유지한다.
- API Key와 인증정보를 코드에 하드코딩하지 않는다.
- Frontend는 화면 표시와 API 호출에 집중하고 Backend 비즈니스 로직을 복제하지 않는다.
- Backend 도메인은 `controller`, `service`, `repository/mapper`, `dto`, `domain` 역할을 명확히 분리한다.
- 업로드 파일 삭제 시 파일, PostgreSQL 메타데이터, 벡터 색인, 그래프 색인의 정합성을 함께 확인한다.
- 기존 표준 패턴을 바꾸는 생성 규칙은 관련 테스트와 함께 반영한다.

## 12. 현재 제약 및 공유 전 확인 사항

이 절은 외부 배포 문서에서는 제거하거나 별도 Backlog로 옮긴다.

- Frontend의 `AppRoutes`가 현재 템플릿 생성 화면만 반환하므로 다른 페이지로 이동하는 정식 라우팅 구성이 필요하다.
- RAG 서버는 운영용 Vector DB가 아니라 로컬 JSON 저장소와 해시 임베딩을 사용한다. 대용량/다중 인스턴스 운영 전 Qdrant 및 실제 임베딩 모델 연결이 필요하다.
- `application.yml`의 프로젝트 경로와 filesystem MCP 루트에 Windows 로컬 절대 경로가 포함되어 있다. 개발자별 환경변수나 별도 프로필로 분리해야 한다.
- 개발 편의를 위한 PostgreSQL/Neo4j 기본 비밀번호와 OpenAI SSL `trust-all` 기본값은 운영 프로필에서 제거 또는 비활성화해야 한다.
- 인증/인가 구성이 확인되지 않으므로 사내 공유 범위와 API 접근 정책을 결정해야 한다.
- 현재 RAG 시작 문서 일부 경로가 `C:\workspace`를 기준으로 되어 있어 실제 저장소 경로와 통일이 필요하다.
- `requirements.txt`의 일부 대형 ML/Qdrant 의존성은 현재 코드 경로에서 사용되지 않는다. 실제 전환 계획에 맞춰 정리한다.

## 13. 문서 보완 예정 항목

- 배포 환경별 구성도와 운영 Runbook
- API 상세 스펙 및 오류 코드
- PostgreSQL/Neo4j 설치 또는 Docker Compose 가이드
- 인증/권한 모델
- 데이터 보존 및 삭제 정책
- RAG 품질 평가 기준과 표준 문서 등록 가이드
- CI/CD 파이프라인과 브랜치/리뷰 정책
- 담당 팀 및 장애 연락 체계

---

### 문서 변경 이력

| 버전 | 일자 | 작성자 | 내용 |
|---|---|---|---|
| 0.1 | 2026-07-15 | 작성자 기입 | 저장소 기준 최초 초안 |
