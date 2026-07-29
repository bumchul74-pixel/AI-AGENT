import { useEffect, useMemo, useState } from 'react';
import {
  ArrowRight,
  Bot,
  DatabaseZap,
  FileCode2,
  FileStack,
  FolderKanban,
  GitFork,
  History,
  LayoutDashboard,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Trash2,
} from 'lucide-react';
import { fetchGenerationHistory } from '../api/generateApi.js';
import { fetchKnowledgeProjects } from '../api/projectApi.js';
import { fetchRagStats } from '../api/ragApi.js';
import { getLatestSecureCodingScan } from '../api/secureCodingApi.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { formatDateTime } from '../utils/dateUtils.js';

const QUICK_ACTIONS = [
  { id: 'generate', label: '소스 생성', description: '프로젝트 표준을 기반으로 Java 소스를 생성합니다.', icon: Bot },
  { id: 'projects', label: '프로젝트 관리', description: 'Knowledge 프로젝트를 생성하고 범위를 관리합니다.', icon: FolderKanban },
  { id: 'documents', label: '문서 관리', description: '소스와 표준 문서를 업로드하고 색인합니다.', icon: FileStack },
  { id: 'rag', label: 'RAG 조회', description: 'VectorDB에 저장된 코드와 문서를 검색합니다.', icon: DatabaseZap },
  { id: 'javaGraph', label: 'Ontology', description: 'Neo4j 타입·메서드·DB 관계를 탐색합니다.', icon: GitFork },
  { id: 'secureCoding', label: '코드 품질·보안 점검', description: 'Semgrep으로 프로젝트 소스의 취약점을 점검합니다.', icon: ShieldCheck },
  { id: 'dataCleanup', label: 'Data Operations', description: '프로젝트 단위로 색인 데이터를 정리합니다.', icon: Trash2 },
];

const SCAN_STATUS_LABELS = {
  QUEUED: '대기 중',
  RUNNING: '점검 중',
  COMPLETED: '완료',
  COMPLETED_WITH_ERRORS: '일부 오류',
  FAILED: '실패',
};

function metricValue(value) {
  return Number(value ?? 0).toLocaleString();
}

function scanDate(scan) {
  return scan.scannedAt ?? scan.startedAt ?? scan.requestedAt ?? '';
}

export function DashboardPage({ onNavigate }) {
  const [projects, setProjects] = useState([]);
  const [javaFileCount, setJavaFileCount] = useState(0);
  const [history, setHistory] = useState([]);
  const [secureCodingScans, setSecureCodingScans] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function loadDashboard() {
      setIsLoading(true);
      const [projectResult, statsResult, historyResult] = await Promise.allSettled([
        fetchKnowledgeProjects(),
        fetchRagStats(),
        fetchGenerationHistory(),
      ]);
      if (cancelled) return;

      const loadedProjects = projectResult.status === 'fulfilled' ? projectResult.value : [];
      setProjects(loadedProjects);
      setJavaFileCount(statsResult.status === 'fulfilled'
        ? statsResult.value.javaFileCount ?? statsResult.value.java_file_count ?? 0
        : 0);
      setHistory(historyResult.status === 'fulfilled' ? historyResult.value : []);

      const scanResults = await Promise.allSettled(
        loadedProjects.map((project) => getLatestSecureCodingScan(project.projectKey)),
      );
      if (cancelled) return;
      setSecureCodingScans(scanResults
        .filter((result) => result.status === 'fulfilled' && result.value)
        .map((result) => result.value));
      setIsLoading(false);
    }

    loadDashboard();
    return () => { cancelled = true; };
  }, [refreshKey]);

  const documentCount = useMemo(
    () => projects.reduce((total, project) => total + Number(project.documentCount ?? 0), 0),
    [projects],
  );
  const indexedProjectCount = useMemo(
    () => projects.filter((project) => Number(project.documentCount ?? 0) > 0).length,
    [projects],
  );
  const recentHistory = history.slice(0, 5);
  const recentProjects = [...projects]
    .sort((left, right) => String(right.updatedAt ?? '').localeCompare(String(left.updatedAt ?? '')))
    .slice(0, 5);
  const recentSecureCodingScans = [...secureCodingScans]
    .sort((left, right) => String(scanDate(right)).localeCompare(String(scanDate(left))))
    .slice(0, 5);
  const projectNames = useMemo(
    () => new Map(projects.map((project) => [project.projectKey, project.name])),
    [projects],
  );
  const securitySummary = useMemo(
    () => secureCodingScans.reduce((summary, scan) => ({
      findings: summary.findings + Number(scan.findingCount ?? 0),
      passedFiles: summary.passedFiles + Number(scan.passedFiles ?? 0),
      errorFiles: summary.errorFiles + Number(scan.errorFiles ?? 0),
    }), { findings: 0, passedFiles: 0, errorFiles: 0 }),
    [secureCodingScans],
  );

  const metrics = [
    { label: 'Knowledge 프로젝트', value: projects.length, detail: `${indexedProjectCount}개 프로젝트 색인됨`, icon: FolderKanban, tone: 'blue' },
    { label: '색인 문서', value: documentCount, detail: '프로젝트에 연결된 활성 문서', icon: FileStack, tone: 'teal' },
    { label: 'Java 파일', value: javaFileCount, detail: 'RAG 및 Graph에서 확인된 파일', icon: FileCode2, tone: 'violet' },
    { label: '생성 이력', value: history.length, detail: '저장된 Java 생성 결과', icon: History, tone: 'amber' },
  ];

  return (
    <section className="dashboard-page">
      <section className="card dashboard-hero">
        <div className="panel-title">
          <LayoutDashboard size={19} />
          <div>
            <h1>AIP Dashboard</h1>
            <p>프로젝트 색인 현황을 확인하고 주요 AI 개발 작업으로 바로 이동합니다.</p>
          </div>
        </div>
        <Button icon={RefreshCw} variant="secondary" onClick={() => setRefreshKey((value) => value + 1)} disabled={isLoading}>
          새로고침
        </Button>
      </section>

      <section className="dashboard-metric-grid" aria-label="주요 현황">
        {metrics.map((metric) => (
          <article className="card dashboard-metric-card" key={metric.label}>
            <span className={`dashboard-metric-icon ${metric.tone}`}><metric.icon size={20} /></span>
            <div>
              <span>{metric.label}</span>
              <strong>{isLoading && metric.label === 'Knowledge 프로젝트' ? '-' : metricValue(metric.value)}</strong>
              <small>{metric.detail}</small>
            </div>
          </article>
        ))}
      </section>

      <section className="dashboard-main-grid">
        <article className="card dashboard-list-panel">
          <div className="page-heading">
            <div><h2>프로젝트 현황</h2><p>최근 수정된 프로젝트와 색인 문서 수입니다.</p></div>
            <button className="dashboard-text-link" type="button" onClick={() => onNavigate('projects')}>전체 보기 <ArrowRight size={15} /></button>
          </div>
          {isLoading ? <Loading /> : recentProjects.length === 0 ? (
            <div className="empty-result"><strong>등록된 프로젝트가 없습니다.</strong><span>프로젝트 관리에서 첫 프로젝트를 추가해 주세요.</span></div>
          ) : (
            <div className="dashboard-project-list">
              {recentProjects.map((project) => (
                <button type="button" key={project.projectKey} onClick={() => onNavigate('projects')}>
                  <span className="dashboard-project-mark">{project.name.slice(0, 1).toUpperCase()}</span>
                  <span><strong>{project.name}</strong><small>{project.projectKey}</small></span>
                  <em>{metricValue(project.documentCount)} 문서</em>
                </button>
              ))}
            </div>
          )}
        </article>

        <article className="card dashboard-list-panel">
          <div className="page-heading">
            <div><h2>최근 생성 이력</h2><p>최근 생성된 Java 소스 결과입니다.</p></div>
            <button className="dashboard-text-link" type="button" onClick={() => onNavigate('history')}>전체 보기 <ArrowRight size={15} /></button>
          </div>
          {isLoading ? <Loading /> : recentHistory.length === 0 ? (
            <div className="empty-result"><strong>생성 이력이 없습니다.</strong><span>소스 생성에서 첫 코드를 생성해 주세요.</span></div>
          ) : (
            <div className="dashboard-history-list">
              {recentHistory.map((item) => (
                <button type="button" key={item.id} onClick={() => onNavigate('history')}>
                  <span><strong>{item.targetType}</strong><small>{item.prompt}</small></span>
                  <time>{item.createdAt ? formatDateTime(item.createdAt) : '-'}</time>
                </button>
              ))}
            </div>
          )}
        </article>
      </section>

      <section className="card dashboard-security-panel">
        <div className="page-heading">
          <div>
            <h2><ShieldCheck size={19} /> 코드 품질·보안 점검</h2>
            <p>프로젝트별 최신 Secure Coding 점검 결과를 요약합니다.</p>
          </div>
          <button className="dashboard-text-link" type="button" onClick={() => onNavigate('secureCoding')}>
            상세 점검 <ArrowRight size={15} />
          </button>
        </div>

        {isLoading ? <Loading /> : recentSecureCodingScans.length === 0 ? (
          <button className="dashboard-security-empty" type="button" onClick={() => onNavigate('secureCoding')}>
            <ShieldAlert size={22} />
            <span><strong>아직 보안 점검 결과가 없습니다.</strong><small>Secure Coding에서 프로젝트의 첫 점검을 실행해 주세요.</small></span>
            <ArrowRight size={16} />
          </button>
        ) : (
          <div className="dashboard-security-content">
            <div className="dashboard-security-summary" aria-label="보안 점검 요약">
              <div><span>점검 프로젝트</span><strong>{metricValue(secureCodingScans.length)}</strong></div>
              <div className="finding"><span>발견 항목</span><strong>{metricValue(securitySummary.findings)}</strong></div>
              <div className="passed"><span>통과 파일</span><strong>{metricValue(securitySummary.passedFiles)}</strong></div>
              <div className="error"><span>점검 오류</span><strong>{metricValue(securitySummary.errorFiles)}</strong></div>
            </div>
            <div className="dashboard-security-list">
              {recentSecureCodingScans.map((scan) => (
                <button type="button" key={scan.jobId} onClick={() => onNavigate('secureCoding')}>
                  <span>
                    <strong>{projectNames.get(scan.projectKey) ?? scan.projectKey}</strong>
                    <small>{scan.totalFiles}개 파일 점검 · {scan.findingCount}개 항목 발견</small>
                  </span>
                  <span className={`dashboard-scan-status status-${String(scan.status).toLowerCase()}`}>
                    {SCAN_STATUS_LABELS[scan.status] ?? scan.status}
                  </span>
                  <time>{scanDate(scan) ? formatDateTime(scanDate(scan)) : '-'}</time>
                </button>
              ))}
            </div>
          </div>
        )}
      </section>

      <section className="card dashboard-actions-panel">
        <div className="page-heading"><div><h2>빠른 작업</h2><p>현재 업무에 필요한 화면으로 바로 이동합니다.</p></div></div>
        <div className="dashboard-action-grid">
          {QUICK_ACTIONS.map((action) => (
            <button type="button" key={action.id} onClick={() => onNavigate(action.id)}>
              <span><action.icon size={19} /></span>
              <div><strong>{action.label}</strong><small>{action.description}</small></div>
              <ArrowRight size={16} />
            </button>
          ))}
        </div>
      </section>
    </section>
  );
}
