import {
  Activity,
  Bot,
  Database,
  DatabaseZap,
  FileStack,
  FolderKanban,
  Gauge,
  GitFork,
  History,
  HardDrive,
  LibraryBig,
  MessageSquareText,
  Network,
  Presentation,
  Sparkles,
  ShieldCheck,
  Settings2,
  Trash2,
} from 'lucide-react';

export const NAVIGATION_SECTIONS = [
  {
    id: 'workspace',
    type: 'group',
    label: 'AI 작업',
    description: 'AI 대화 · 콘텐츠 생성',
    icon: Sparkles,
    defaultPage: 'chat',
    children: [
      { id: 'chat', label: 'AI 대화', description: 'MCP 도구와 프로젝트 표준 대화', icon: MessageSquareText },
      { id: 'generate', label: '소스 생성', description: 'RAG 기반 Java 코드 생성', icon: Bot },
      { id: 'presentationGenerate', label: 'PPT 생성', description: '템플릿 기반 PPTX 자동 생성', icon: Presentation },
      { id: 'history', label: '생성 이력', description: '생성 결과 조회와 재사용', icon: History },
    ],
  },
  {
    id: 'knowledge',
    type: 'group',
    label: '지식 관리',
    description: '프로젝트 · 문서',
    icon: LibraryBig,
    defaultPage: 'projects',
    children: [
      { id: 'projects', label: '프로젝트 관리', description: 'Knowledge 프로젝트 생성과 관리', icon: FolderKanban },
      { id: 'documents', label: '문서 관리', description: '표준 문서와 소스 색인', icon: FileStack },
    ],
  },
  {
    id: 'dataExplorer',
    type: 'group',
    label: '데이터 탐색',
    description: 'VectorDB · Neo4j 조회',
    icon: Database,
    defaultPage: 'rag',
    children: [
      { id: 'rag', label: 'RAG 조회', description: 'VectorDB 유사도 검색', icon: DatabaseZap },
      { id: 'neo4jExplorer', label: 'Neo4j 데이터 탐색', description: '노드 · 속성 · 관계 상세 조회', icon: Network },
    ],
  },
  {
    id: 'analysis',
    type: 'group',
    label: '분석 및 품질',
    description: '소스 관계 · 보안 점검',
    icon: Network,
    defaultPage: 'javaGraph',
    children: [
      { id: 'javaGraph', label: 'Java Graph', description: '패키지 관계와 영향도 분석', icon: GitFork },
      { id: 'sourceQuality', label: '소스 품질', description: '중복 · 복잡도 · 품질 Gate', icon: Gauge },
      { id: 'secureCoding', label: 'Secure Coding', description: 'Semgrep 프로젝트 보안 점검', icon: ShieldCheck },
    ],
  },
  {
    id: 'dataOperations',
    type: 'group',
    label: '운영 관리',
    description: '시스템 상태 · 설정 · 데이터 정리',
    icon: HardDrive,
    defaultPage: 'systemStatus',
    children: [
      { id: 'systemStatus', label: '시스템 상태', description: '연계 시스템 상태와 응답 시간', icon: Activity },
      { id: 'agentConfiguration', label: 'Agent 설정 관리', description: 'Agent · Capability 실행 정책', icon: Settings2 },
      { id: 'dataCleanup', label: '통합 데이터 삭제', description: 'PostgreSQL · VectorDB · Neo4j 정리', icon: Trash2 },
    ],
  },
];

export function findNavigationSection(pageId) {
  return NAVIGATION_SECTIONS.find((section) =>
    section.defaultPage === pageId && section.type === 'page'
      || (section.children ?? []).some((item) => item.id === pageId),
  );
}