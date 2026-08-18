import { useEffect, useRef, useState } from 'react';
import { Activity, AlertTriangle, CheckCircle2, Gauge, GitCompareArrows, RefreshCw, RotateCcw, Save } from 'lucide-react';
import { notifyApp } from '../api/apiClient.js';
import { reindexProjectJavaDocuments } from '../api/documentApi.js';
import { fetchKnowledgeProjects } from '../api/projectApi.js';
import {
  evaluateSourceQuality,
  fetchSourceQualityDuplicateGroup,
  fetchSourceQualityMethodDetail,
  fetchSourceQuality,
  updateSourceQualityThresholds,
} from '../api/sourceQualityApi.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { Modal } from '../components/common/Modal.jsx';
import { ProjectSelect } from '../components/common/ProjectSelect.jsx';
import { formatDateTime } from '../utils/dateUtils.js';

const EMPTY_THRESHOLDS = {
  cyclomaticComplexity: 10,
  cognitiveComplexity: 15,
  duplicateRatio: 10,
  minimumDuplicateLines: 5,
};

function notifyEvaluationComplete(result, prefix = '', preferredVariant = 'success') {
  const passed = result?.gate?.status === 'PASS';
  const reasonCount = result?.gate?.reasons?.length ?? 0;
  const evaluationMessage = passed
    ? '품질 평가를 완료했습니다. 모든 임계치를 충족했습니다.'
    : `품질 평가를 완료했습니다. 임계치 초과 항목 ${reasonCount}건을 결과에 표시했습니다.`;
  notifyApp(
    [prefix, evaluationMessage].filter(Boolean).join(' '),
    preferredVariant,
  );
}

export function SourceQualityPage() {
  const [projects, setProjects] = useState([]);
  const [projectKey, setProjectKey] = useState('');
  const [dashboard, setDashboard] = useState(null);
  const [thresholds, setThresholds] = useState(EMPTY_THRESHOLDS);
  const [isLoading, setIsLoading] = useState(true);
  const [isEvaluating, setIsEvaluating] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isReindexing, setIsReindexing] = useState(false);
  const [showReindexConfirm, setShowReindexConfirm] = useState(false);
  const [reindexProgress, setReindexProgress] = useState({ completed: 0, total: 0, currentFile: '' });
  const [duplicateDetail, setDuplicateDetail] = useState({ open: false, group: null, data: null, loading: false });
  const duplicateRequestRef = useRef(null);
  const [complexMethodDetail, setComplexMethodDetail] = useState({ open: false, method: null, data: null, loading: false });
  const complexMethodRequestRef = useRef(null);

  useEffect(() => {
    let ignore = false;
    fetchKnowledgeProjects()
      .then((items) => {
        if (ignore) return;
        const next = Array.isArray(items) ? items : [];
        setProjects(next);
        setProjectKey((current) => current || next[0]?.projectKey || '');
      })
      .catch(() => {})
      .finally(() => { if (!ignore) setIsLoading(false); });
    return () => { ignore = true; };
  }, []);

  useEffect(() => {
    duplicateRequestRef.current?.abort();
    complexMethodRequestRef.current?.abort();
    setDuplicateDetail({ open: false, group: null, data: null, loading: false });
    setComplexMethodDetail({ open: false, method: null, data: null, loading: false });
    if (!projectKey) {
      setDashboard(null);
      return undefined;
    }
    const controller = new AbortController();
    setIsLoading(true);
    fetchSourceQuality(projectKey, controller.signal)
      .then((result) => {
        setDashboard(result);
        setThresholds(result.thresholds ?? EMPTY_THRESHOLDS);
      })
      .catch(() => {})
      .finally(() => setIsLoading(false));
    return () => controller.abort();
  }, [projectKey]);

  async function handleEvaluate() {
    setIsEvaluating(true);
    try {
      const result = await evaluateSourceQuality(projectKey);
      setDashboard(result);
      setThresholds(result.thresholds ?? EMPTY_THRESHOLDS);
      notifyEvaluationComplete(result);

    } finally {
      setIsEvaluating(false);
    }
  }

  async function handleThresholdSave() {
    setIsSaving(true);
    try {
      const result = await updateSourceQualityThresholds(projectKey, {
        cyclomaticComplexity: Number(thresholds.cyclomaticComplexity),
        cognitiveComplexity: Number(thresholds.cognitiveComplexity),
        duplicateRatio: Number(thresholds.duplicateRatio),
        minimumDuplicateLines: Number(thresholds.minimumDuplicateLines),
      });
      setDashboard(result);
      setThresholds(result.thresholds);
      notifyApp('소스 품질 임계치를 저장했습니다.', 'success');
    } finally {
      setIsSaving(false);
    }
  }

  async function handleReindex() {
    setShowReindexConfirm(false);
    setIsReindexing(true);
    setReindexProgress({ completed: 0, total: 0, currentFile: '' });
    try {
      const result = await reindexProjectJavaDocuments(projectKey, setReindexProgress);
      if (result.total === 0) {
        notifyApp('\uC7AC\uC0C9\uC778\uD560 Java \uBB38\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.', 'warning');
        return;
      }

      const quality = await evaluateSourceQuality(projectKey);
      setDashboard(quality);
      setThresholds(quality.thresholds ?? EMPTY_THRESHOLDS);
      notifyEvaluationComplete(
        quality,
        result.failureCount === 0
          ? 'Java \uBB38\uC11C ' + result.successCount + '\uAC74\uC744 \uC7AC\uC0C9\uC778\uD558\uACE0 \uD488\uC9C8 \uD3C9\uAC00\uB97C \uAC31\uC2E0\uD588\uC2B5\uB2C8\uB2E4.'
          : 'Java \uBB38\uC11C ' + result.successCount + '\uAC74 \uC7AC\uC0C9\uC778 \uC644\uB8CC, ' + result.failureCount + '\uAC74 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.',
        result.failureCount === 0 ? 'success' : 'warning',
      );
    } finally {
      setIsReindexing(false);
    }
  }

  function closeComplexMethodDetail() {
    complexMethodRequestRef.current?.abort();
    complexMethodRequestRef.current = null;
    setComplexMethodDetail({ open: false, method: null, data: null, loading: false });
  }

  async function openComplexMethodDetail(method) {
    complexMethodRequestRef.current?.abort();
    const controller = new AbortController();
    complexMethodRequestRef.current = controller;
    setComplexMethodDetail({ open: true, method, data: null, loading: true });
    try {
      const data = await fetchSourceQualityMethodDetail(projectKey, method.methodUid, controller.signal);
      if (!controller.signal.aborted) {
        setComplexMethodDetail({ open: true, method, data, loading: false });
      }
    } catch (exception) {
      if (!controller.signal.aborted) closeComplexMethodDetail();
    } finally {
      if (complexMethodRequestRef.current === controller) complexMethodRequestRef.current = null;
    }
  }

  function closeDuplicateDetail() {
    duplicateRequestRef.current?.abort();
    duplicateRequestRef.current = null;
    setDuplicateDetail({ open: false, group: null, data: null, loading: false });
  }

  async function openDuplicateDetail(group) {
    duplicateRequestRef.current?.abort();
    const controller = new AbortController();
    duplicateRequestRef.current = controller;
    setDuplicateDetail({ open: true, group, data: null, loading: true });
    try {
      const data = await fetchSourceQualityDuplicateGroup(projectKey, group.type, group.hash, controller.signal);
      if (!controller.signal.aborted) {
        setDuplicateDetail({ open: true, group, data, loading: false });
      }
    } catch (exception) {
      if (!controller.signal.aborted) closeDuplicateDetail();
    } finally {
      if (duplicateRequestRef.current === controller) duplicateRequestRef.current = null;
    }
  }

  function updateThreshold(name, value) {
    setThresholds((current) => ({ ...current, [name]: value }));
  }

  const summary = dashboard?.summary;
  const gate = dashboard?.gate;
  const gatePassed = gate?.status === 'PASS';
  const selectedProjectName = projects.find((project) => project.projectKey === projectKey)?.projectName ?? projectKey;

  return (
    <section className="source-quality-page">
      <section className="card source-quality-hero">
        <div className="panel-title">
          <Gauge size={20} />
          <div><h1>소스 품질</h1><p>Java 메서드 중복, 복잡도, 품질 추이와 프로젝트 Gate를 확인합니다.</p></div>
        </div>
        <div className="source-quality-actions">
          <ProjectSelect projects={projects} value={projectKey} onChange={setProjectKey} />
          <Button variant="secondary" icon={RotateCcw} onClick={() => setShowReindexConfirm(true)}
            disabled={!projectKey || isLoading || isEvaluating || isSaving || isReindexing}>
            {'Java \uC804\uCCB4 \uC7AC\uC0C9\uC778'}
          </Button>
          <Button icon={RefreshCw} onClick={handleEvaluate} disabled={!projectKey || isLoading || isEvaluating || isReindexing}>
            {isEvaluating ? '평가 중...' : '품질 평가'}
          </Button>
        </div>
      </section>

      {isReindexing ? (
        <section className="card source-quality-reindex-progress" aria-live="polite">
          <div><strong>{'Java \uBB38\uC11C \uC7AC\uC0C9\uC778 \uC911'}</strong><span>{reindexProgress.completed} / {reindexProgress.total || '?'}{'\uAC74'}</span></div>
          <div className="source-quality-progress-track"><span style={{ width: String(reindexProgress.total ? (reindexProgress.completed / reindexProgress.total) * 100 : 0) + '%' }} /></div>
          <small title={reindexProgress.currentFile}>{reindexProgress.currentFile || '\uB300\uC0C1 \uBB38\uC11C\uB97C \uC870\uD68C\uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4.'}</small>
        </section>
      ) : null}

      {isLoading ? <Loading /> : !projectKey ? (
        <div className="empty-result"><strong>분석할 프로젝트를 먼저 등록해 주세요.</strong></div>
      ) : !dashboard ? null : (
        <>

          <section className="source-quality-summary">
            {[
              ['전체 메서드', summary?.totalMethodCount ?? 0],
              ['중복 메서드', summary?.duplicateMethodCount ?? 0],
              ['중복 비율', `${summary?.duplicateRatio ?? 0}%`],
              ['고복잡도', summary?.highComplexityCount ?? 0],
              ['최대 순환 복잡도', summary?.maxCyclomaticComplexity ?? 0],
              ['최대 인지 복잡도', summary?.maxCognitiveComplexity ?? 0],
            ].map(([label, value]) => <article className="card" key={label}><span>{label}</span><strong>{value}</strong></article>)}
          </section>

          <section className={`card source-quality-gate ${gatePassed ? 'is-pass' : 'is-fail'}`} aria-live="polite">
            <div className="source-quality-gate-heading">
              {gatePassed ? <CheckCircle2 size={22} aria-hidden="true" /> : <AlertTriangle size={22} aria-hidden="true" />}
              <div>
                <h2>품질 평가 결과</h2>
                <p>{gatePassed ? '설정한 품질 임계치를 모두 충족했습니다.' : '임계치를 초과한 항목을 확인해 주세요.'}</p>
              </div>
              <strong className="source-quality-gate-status">{gate?.status ?? '미평가'}</strong>
            </div>
            {!gatePassed && gate?.reasons?.length ? (
              <ul>{gate.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
            ) : null}
          </section>

          <section className="card source-quality-thresholds">
            <div className="page-heading"><div><h2>프로젝트 임계치</h2><p>초과 메서드 또는 중복 비율이 있으면 Gate가 실패합니다.</p></div></div>
            <div className="source-quality-threshold-grid">
              {[
                ['cyclomaticComplexity', '순환 복잡도', 1, 999],
                ['cognitiveComplexity', '인지 복잡도', 1, 999],
                ['duplicateRatio', '중복 비율 (%)', 0, 100],
                ['minimumDuplicateLines', '최소 중복 라인', 1, 999],
              ].map(([name, label, min, max]) => (
                <label className="field" key={name}><span>{label}</span><input type="number" min={min} max={max}
                  value={thresholds[name]} onChange={(event) => updateThreshold(name, event.target.value)} /></label>
              ))}
              <Button icon={Save} onClick={handleThresholdSave} disabled={isSaving}>{isSaving ? '저장 중...' : '임계치 저장'}</Button>
            </div>
          </section>

          <section className="source-quality-grid">
            <section className="card source-quality-panel">
              <div className="page-heading"><div><h2><GitCompareArrows size={18} /> 중복 메서드 그룹</h2><p>Exact와 변수명·리터럴을 일반화한 Structural 후보입니다.</p></div></div>
              <div className="source-quality-list">
                {dashboard.duplicateGroups?.length ? dashboard.duplicateGroups.map((group) => (
                  <button className="quality-duplicate-group" type="button" key={`${group.type}-${group.hash}`}
                    onClick={() => openDuplicateDetail(group)}>
                    <span className={`quality-type type-${group.type.toLowerCase()}`}>{group.type}</span>
                    <span><strong>{group.methodCount}개 메서드</strong><small>{group.methods[0]?.declaringType}.{group.methods[0]?.signature}</small></span>
                    <small>{group.duplicatedLineCount} lines</small>
                  </button>
                )) : <div className="quality-empty">현재 임계치의 중복 후보가 없습니다.</div>}
              </div>
            </section>

            <section className="card source-quality-panel">
              <div className="page-heading"><div><h2><Activity size={18} /> 고복잡도 메서드</h2><p>순환 또는 인지 복잡도가 임계치를 초과한 순서입니다.</p></div></div>
              <div className="source-quality-list">
                {dashboard.highComplexityMethods?.length ? dashboard.highComplexityMethods.map((method) => (
                  <button className="quality-method complex quality-complex-method" type="button" key={method.methodUid}
                    onClick={() => openComplexMethodDetail(method)}>
                    <div><strong>{method.declaringType}.{method.signature}</strong><code>{method.filePath}:{method.startLine}-{method.endLine}</code></div>
                    <span>C {method.cyclomaticComplexity}</span><span>Cog {method.cognitiveComplexity}</span>
                  </button>
                )) : <div className="quality-empty">복잡도 임계치를 초과한 메서드가 없습니다.</div>}
              </div>
            </section>
          </section>

          <section className="card source-quality-panel">
            <div className="page-heading"><div><h2>품질 추이</h2><p>요약 값이 달라진 평가만 최대 30건 저장합니다.</p></div></div>
            <div className="quality-trend">
              {dashboard.trend?.length ? [...dashboard.trend].reverse().map((point) => (
                <article key={point.id} title={formatDateTime(point.createdAt)}>
                  <div className="trend-bars"><span style={{ height: `${Math.min(100, point.duplicateRatio)}%` }} />
                    <i style={{ height: `${Math.min(100, point.highComplexityCount * 8)}%` }} /></div>
                  <strong className={point.gateStatus === 'PASS' ? 'trend-pass' : 'trend-fail'}>{point.gateStatus}</strong>
                  <small>{point.duplicateRatio}% · {point.highComplexityCount}</small>
                </article>
              )) : <div className="quality-empty">품질 평가를 실행하면 첫 추이가 저장됩니다.</div>}
            </div>
          </section>
        </>
      )}
      <Modal open={complexMethodDetail.open} title="고복잡도 메서드 소스" onClose={closeComplexMethodDetail}
        className="source-quality-complex-modal">
        {complexMethodDetail.loading ? <Loading /> : complexMethodDetail.data ? (
          <div className="quality-complex-detail">
            <header>
              <strong>{complexMethodDetail.data.declaringType}.{complexMethodDetail.data.signature}</strong>
              <code>{complexMethodDetail.data.filePath}:{complexMethodDetail.data.startLine}-{complexMethodDetail.data.endLine}</code>
            </header>
            <div className="quality-complex-metrics">
              <span>순환 <strong>{complexMethodDetail.data.cyclomaticComplexity}</strong></span>
              <span>인지 <strong>{complexMethodDetail.data.cognitiveComplexity}</strong></span>
              <span>최대 중첩 <strong>{complexMethodDetail.data.maxNestingDepth}</strong></span>
              <span>분기 <strong>{complexMethodDetail.data.branchCount}</strong></span>
              <span>호출 <strong>{complexMethodDetail.data.callCount}</strong></span>
              <span>라인 <strong>{complexMethodDetail.data.lineCount}</strong></span>
            </div>
            <pre className="quality-complex-source"><code>{complexMethodDetail.data.methodBody || '// 메서드 본문이 저장되지 않았습니다. Java 전체 재색인을 실행해 주세요.'}</code></pre>
          </div>
        ) : null}
      </Modal>
      <Modal open={duplicateDetail.open} title="중복 메서드 내용 비교" onClose={closeDuplicateDetail}
        className="source-quality-duplicate-modal">
        {duplicateDetail.loading ? <Loading /> : duplicateDetail.data ? (
          <div className="quality-duplicate-detail">
            <div className="quality-duplicate-detail-summary">
              <span className={`quality-type type-${duplicateDetail.data.type.toLowerCase()}`}>{duplicateDetail.data.type}</span>
              <strong>{duplicateDetail.data.methodCount}개 메서드가 동일 그룹으로 판정되었습니다.</strong>
              <p>{duplicateDetail.data.type === 'EXACT'
                ? '주석과 공백을 제외한 메서드 내용이 같습니다.'
                : '변수명과 리터럴 차이를 일반화했을 때 구조가 같습니다.'}</p>
            </div>
            <div className="quality-method-comparison">
              {duplicateDetail.data.methods.map((method) => (
                <article key={method.methodUid}>
                  <header>
                    <strong>{method.declaringType}.{method.signature}</strong>
                    <code>{method.filePath}:{method.startLine}-{method.endLine}</code>
                  </header>
                  <pre><code>{method.methodBody || '// 메서드 본문이 저장되지 않았습니다.'}</code></pre>
                </article>
              ))}
            </div>
          </div>
        ) : null}
      </Modal>
      <Modal open={showReindexConfirm} title={'Java \uC804\uCCB4 \uC7AC\uC0C9\uC778'} onClose={() => setShowReindexConfirm(false)}>
        <div className="source-quality-reindex-confirm">
          <AlertTriangle size={28} />
          <div>
            <strong>{selectedProjectName || '\uC120\uD0DD\uD55C \uD504\uB85C\uC81D\uD2B8'}{'\uC758 Java \uBB38\uC11C\uB97C \uBAA8\uB450 \uC7AC\uC0C9\uC778\uD560\uAE4C\uC694?'}</strong>
            <p>{'\uAE30\uC874 \uBB38\uC11C\uBCC4 Vector \uC0C9\uC778\uACFC Java \uADF8\uB798\uD504\uB97C \uCD5C\uC2E0 \uBD84\uC11D \uACB0\uACFC\uB85C \uAD50\uCCB4\uD569\uB2C8\uB2E4. \uBB38\uC11C \uC218\uC5D0 \uB530\uB77C \uC2DC\uAC04\uC774 \uAC78\uB9B4 \uC218 \uC788\uC2B5\uB2C8\uB2E4.'}</p>
          </div>
        </div>
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setShowReindexConfirm(false)}>{'\uCDE8\uC18C'}</Button>
          <Button icon={RotateCcw} onClick={handleReindex}>{'\uC7AC\uC0C9\uC778 \uC2DC\uC791'}</Button>
        </div>
      </Modal>
    </section>
  );
}
