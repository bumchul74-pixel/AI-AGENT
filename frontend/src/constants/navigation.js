import {
  Bot,
  DatabaseZap,
  FileStack,
  FolderKanban,
  GitFork,
  History,
  HardDrive,
  LibraryBig,
  MessageSquareText,
  Network,
  Sparkles,
  ShieldCheck,
  Trash2,
} from 'lucide-react';

export const NAVIGATION_SECTIONS = [
  {
    id: 'workspace',
    label: 'AI Workspace',
    description: '소스 생성 · AI 질의',
    icon: Sparkles,
    defaultPage: 'generate',
    children: [
      { id: 'generate', label: '소스 생성', description: 'RAG 기반 Java 코드 생성', icon: Bot },
      { id: 'chat', label: 'AI 질의', description: '프로젝트 코드와 표준 질의', icon: MessageSquareText },
      { id: 'history', label: '생성 이력', description: '생성 결과 조회와 재사용', icon: History },
    ],
  },
  {
    id: 'knowledge',
    label: 'Knowledge',
    description: '검색 · 문서 관리',
    icon: LibraryBig,
    defaultPage: 'rag',
    children: [
      { id: 'rag', label: 'RAG 조회', description: 'VectorDB 의미 검색', icon: DatabaseZap },
      { id: 'projects', label: '프로젝트 관리', description: 'Knowledge 프로젝트 생성과 관리', icon: FolderKanban },
      { id: 'documents', label: '문서 관리', description: '표준 문서와 소스 색인', icon: FileStack },
    ],
  },
  {
    id: 'ontology',
    label: 'Ontology',
    description: '관계 · 영향도 분석',
    icon: Network,
    defaultPage: 'javaGraph',
    children: [
      { id: 'javaGraph', label: 'Java Graph', description: 'Neo4j 관계와 영향도 탐색', icon: GitFork },
    ],
  },
  {
    id: 'quality',
    label: 'Quality & Security',
    description: '코드 품질 · 보안 점검',
    icon: ShieldCheck,
    defaultPage: 'secureCoding',
    children: [
      { id: 'secureCoding', label: 'Secure Coding', description: 'Semgrep 프로젝트 보안 점검', icon: ShieldCheck },
    ],
  },
  {
    id: 'dataOperations',
    label: 'Data Operations',
    description: '저장소 정리 · 삭제',
    icon: HardDrive,
    defaultPage: 'dataCleanup',
    children: [
      {
        id: 'dataCleanup',
        label: '통합 데이터 삭제',
        description: 'PostgreSQL · VectorDB · Neo4j 정리',
        icon: Trash2,
      },
    ],
  },
];

export function findNavigationSection(pageId) {
  return NAVIGATION_SECTIONS.find((section) =>
    section.children.some((item) => item.id === pageId),
  );
}
