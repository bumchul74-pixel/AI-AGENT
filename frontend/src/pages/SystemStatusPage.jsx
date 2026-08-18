import { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  Bot,
  BrainCircuit,
  Clock3,
  Database,
  Network,
  RefreshCw,
  Search,
  Server,
} from 'lucide-react';
import { checkSystemStatus, fetchSystemStatus } from '../api/systemStatusApi.js';
import { notifyApp } from '../api/apiClient.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import {
  SYSTEM_STATUS_LABELS,
  SystemStatusBadge,
} from '../components/system/SystemStatusBadge.jsx';
import { formatDateTime } from '../utils/dateUtils.js';

const SYSTEM_ICONS = {
  backend: Server,
  postgresql: Database,
  rag: Search,
  'ai-mcp': Network,
  easyocr: Bot,
  neo4j: Network,
  llm: BrainCircuit,
};

const CHECK_TYPE_LABELS = {
  LIVENESS: 'Liveness',
  READINESS: 'Readiness',
  HTTP_HEALTH: 'HTTP Health',
  CONNECTIVITY: 'Connectivity',
  CONFIGURATION: 'Configuration',
};

export function SystemStatusPage() {
  const [snapshot, setSnapshot] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isChecking, setIsChecking] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetchSystemStatus(controller.signal)
      .then(setSnapshot)
      .catch(() => {})
      .finally(() => setIsLoading(false));
    return () => controller.abort();
  }, []);

  const counts = useMemo(() => ([
    { label: '전체', value: snapshot?.totalCount ?? 0, status: 'TOTAL' },
    { label: '정상', value: snapshot?.upCount ?? 0, status: 'UP' },
    { label: '일부 장애', value: snapshot?.degradedCount ?? 0, status: 'DEGRADED' },
    { label: '장애', value: snapshot?.downCount ?? 0, status: 'DOWN' },
    { label: '미확인', value: snapshot?.unknownCount ?? 0, status: 'UNKNOWN' },
  ]), [snapshot]);

  async function handleCheck() {
    setIsChecking(true);
    try {
      const checked = await checkSystemStatus();
      setSnapshot(checked);
      notifyApp('연계 시스템 상태 점검을 완료했습니다.', checked.status === 'UP' ? 'success' : 'warning');
    } finally {
      setIsChecking(false);
    }
  }

  return (
    <section className="system-status-page">
      <section className="card system-status-hero">
        <div className="panel-title">
          <Activity size={19} />
          <div>
            <h1>시스템 상태</h1>
            <p>AI-AGENT와 주요 연계 시스템의 최근 상태를 확인합니다.</p>
          </div>
        </div>
        <div className="system-status-hero-actions">
          {snapshot && <SystemStatusBadge status={snapshot.status} />}
          <Button icon={RefreshCw} onClick={handleCheck} disabled={isLoading || isChecking}>
            {isChecking ? '점검 중...' : '지금 점검'}
          </Button>
        </div>
      </section>

      <section className="system-status-notice" aria-label="점검 방식 안내">
        <Clock3 size={16} />
        <span>화면 조회는 캐시된 최근 결과를 사용합니다. 지금 점검을 누르면 모든 연계 상태를 즉시 다시 확인합니다.</span>
      </section>

      {isLoading ? <Loading /> : !snapshot ? null : (
        <>
          <section className="system-status-count-grid" aria-label="시스템 상태 요약">
            {counts.map((item) => (
              <article className={`card system-status-count status-${item.status.toLowerCase()}`} key={item.label}>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
              </article>
            ))}
          </section>

          <section className="card system-status-list-panel">
            <div className="page-heading">
              <div>
                <h2>연계 시스템</h2>
                <p>최근 점검 {snapshot.checkedAt ? formatDateTime(snapshot.checkedAt) : '-'}</p>
              </div>
              <span className="system-status-overall">전체 상태: {SYSTEM_STATUS_LABELS[snapshot.status] ?? '미확인'}</span>
            </div>

            {snapshot.systems?.length === 0 ? (
              <div className="empty-result"><strong>등록된 상태 점검 대상이 없습니다.</strong></div>
            ) : (
              <div className="system-status-list">
                {snapshot.systems.map((system) => {
                  const Icon = SYSTEM_ICONS[system.id] ?? Server;
                  return (
                    <article className="system-status-row" key={system.id}>
                      <span className={`system-status-icon status-${String(system.status).toLowerCase()}`}><Icon size={18} /></span>
                      <div className="system-status-main">
                        <div>
                          <strong>{system.name}</strong>
                          <span className={system.critical ? 'dependency-critical' : 'dependency-optional'}>
                            {system.critical ? '핵심' : '선택'}
                          </span>
                        </div>
                        <small>{system.message}</small>
                      </div>
                      <div className="system-status-meta">
                        <span>{CHECK_TYPE_LABELS[system.checkType] ?? system.checkType}</span>
                        <span>{Number(system.latencyMs ?? 0).toLocaleString()} ms</span>
                      </div>
                      <SystemStatusBadge status={system.status} />
                    </article>
                  );
                })}
              </div>
            )}
          </section>

          <section className="system-status-guidance">
            <article className="card"><strong>Liveness / Readiness</strong><span>프로세스 생존 여부와 핵심 저장소 준비 상태를 확인합니다.</span></article>
            <article className="card"><strong>Connectivity</strong><span>endpoint 연결 가능 여부이며 실제 업무 요청 성공을 완전히 보장하지는 않습니다.</span></article>
            <article className="card"><strong>Configuration</strong><span>LLM 설정 완전성만 확인하며 비용이 발생하는 실제 생성 요청은 보내지 않습니다.</span></article>
          </section>
        </>
      )}
    </section>
  );
}
