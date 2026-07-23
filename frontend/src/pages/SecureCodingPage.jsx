import { useEffect, useMemo, useRef, useState } from 'react';
import { Download, FileCode, Play, RefreshCw, Search, ShieldCheck } from 'lucide-react';
import {
  exportSecureCodingResults,
  getLatestSecureCodingScan,
  getSecureCodingScan,
  getSecureCodingSource,
  scanProjectSecureCoding,
} from '../api/secureCodingApi.js';
import { isApiRequestError } from '../api/apiClient.js';
import { fetchKnowledgeProjects } from '../api/projectApi.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { Modal } from '../components/common/Modal.jsx';
import { ProjectSelect } from '../components/common/ProjectSelect.jsx';
import { formatDateTime } from '../utils/dateUtils.js';

const STATUS_LABELS = { FINDING: '취약점', PASSED: '통과', ERROR: '검사 오류' };
const SEVERITY_OPTIONS = ['ALL', 'ERROR', 'WARNING', 'INFO'];
const ACTIVE_SCAN_STATUSES = new Set(['QUEUED', 'RUNNING']);

function location(row) {
  if (row.startLine == null) return '-';
  return row.startColumn == null ? `${row.startLine}` : `${row.startLine}:${row.startColumn}`;
}

function fileDisplayParts(value) {
  const fullPath = String(value ?? '');
  const separatorIndex = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
  if (separatorIndex < 0) return { fileName: fullPath, filePath: '-' };
  return {
    fileName: fullPath.slice(separatorIndex + 1),
    filePath: fullPath.slice(0, separatorIndex) || '-',
  };
}

function SourceCodeViewer({ content, startLine, endLine }) {
  const containerRef = useRef(null);
  const highlightedLineRef = useRef(null);
  const firstLine = Number.isInteger(startLine) && startLine > 0 ? startLine : null;
  const lastLine = Number.isInteger(endLine) && endLine >= firstLine ? endLine : firstLine;
  const lines = content.split(/\r\n|\n|\r/);

  useEffect(() => {
    if (!highlightedLineRef.current || !containerRef.current) return undefined;
    const frameId = window.requestAnimationFrame(() => {
      const container = containerRef.current;
      const highlightedLine = highlightedLineRef.current;
      if (!container || !highlightedLine) return;
      container.scrollTop = Math.max(
        0,
        highlightedLine.offsetTop - (container.clientHeight / 2) + (highlightedLine.clientHeight / 2),
      );
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [content, firstLine]);

  return (
    <pre className="java-source-code secure-coding-source-code" ref={containerRef}>
      {lines.map((line, index) => {
        const lineNumber = index + 1;
        const highlighted = firstLine != null && lineNumber >= firstLine && lineNumber <= lastLine;
        return (
          <span
            className={`secure-coding-source-line${highlighted ? ' is-highlighted' : ''}`}
            key={lineNumber}
            ref={highlighted && lineNumber === firstLine ? highlightedLineRef : null}
          >
            <span className="secure-coding-line-number">{lineNumber}</span>
            <span className="secure-coding-line-text">{line || ' '}</span>
          </span>
        );
      })}
    </pre>
  );
}

function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function SecureCodingPage() {
  const [projects, setProjects] = useState([]);
  const [projectKey, setProjectKey] = useState('');
  const [scanResult, setScanResult] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [severity, setSeverity] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [isLoading, setIsLoading] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [sourceModal, setSourceModal] = useState({
    open: false,
    row: null,
    content: '',
    isLoading: false,
    error: '',
  });
  const sourceRequestIdRef = useRef(0);

  function closeSourceModal() {
    sourceRequestIdRef.current += 1;
    setSourceModal({ open: false, row: null, content: '', isLoading: false, error: '' });
  }

  async function openSourceModal(row) {
    if (row?.documentId == null) return;
    const requestId = sourceRequestIdRef.current + 1;
    sourceRequestIdRef.current = requestId;
    setSourceModal({ open: true, row, content: '', isLoading: true, error: '' });
    try {
      const content = await getSecureCodingSource(row.documentId);
      if (sourceRequestIdRef.current !== requestId) return;
      setSourceModal({ open: true, row, content, isLoading: false, error: '' });
    } catch (exception) {
      if (sourceRequestIdRef.current !== requestId) return;
      if (isApiRequestError(exception)) {
        closeSourceModal();
        return;
      }
      setSourceModal({
        open: true,
        row,
        content: '',
        isLoading: false,
        error: exception.message || '소스 파일을 표시할 수 없습니다.',
      });
    }
  }

  useEffect(() => {
    let cancelled = false;
    fetchKnowledgeProjects()
      .then((items) => {
        if (cancelled) return;
        setProjects(items);
        setProjectKey((current) => current || items[0]?.projectKey || '');
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setScanResult(null);
    if (!projectKey) return () => { cancelled = true; };
    setIsLoading(true);
    getLatestSecureCodingScan(projectKey)
      .then((result) => { if (!cancelled) setScanResult(result); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setIsLoading(false); });
    return () => { cancelled = true; };
  }, [projectKey]);

  useEffect(() => {
    if (!scanResult?.jobId || !ACTIVE_SCAN_STATUSES.has(scanResult.status)) return undefined;
    let cancelled = false;
    let timerId;
    async function poll() {
      try {
        const next = await getSecureCodingScan(scanResult.jobId);
        if (cancelled) return;
        setScanResult(next);
        if (ACTIVE_SCAN_STATUSES.has(next.status)) timerId = window.setTimeout(poll, 1500);
      } catch {}
    }
    timerId = window.setTimeout(poll, 1000);
    return () => { cancelled = true; window.clearTimeout(timerId); };
  }, [scanResult?.jobId, scanResult?.status]);

  const visibleRows = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return (scanResult?.results ?? []).filter((row) => {
      const matchesSeverity = severity === 'ALL' || row.severity === severity;
      const matchesStatus = status === 'ALL' || row.status === status;
      const matchesKeyword = !normalized || [row.fileName, row.ruleId, row.message]
        .some((value) => String(value ?? '').toLowerCase().includes(normalized));
      return matchesSeverity && matchesStatus && matchesKeyword;
    });
  }, [scanResult, keyword, severity, status]);

  async function handleScan() {
    if (!projectKey) return;
    setIsLoading(true);
    try {
      setScanResult(await scanProjectSecureCoding(projectKey));
    } catch {} finally {
      setIsLoading(false);
    }
  }

  const isScanning = ACTIVE_SCAN_STATUSES.has(scanResult?.status);

  async function handleExport() {
    if (visibleRows.length === 0) return;
    setIsExporting(true);
    try {
      const blob = await exportSecureCodingResults(projectKey, visibleRows);
      downloadBlob(blob, `secure-coding-${projectKey}.xlsx`);
    } catch {} finally {
      setIsExporting(false);
    }
  }

  return (
    <section className="secure-coding-page">
      <div className="card secure-coding-intro">
        <div className="panel-title">
          <ShieldCheck size={20} />
          <div>
            <h1>Secure Coding 점검</h1>
            <p>프로젝트에 업로드된 Java 소스코드를 Semgrep 규칙으로 점검합니다.</p>
          </div>
        </div>
        <div className="secure-coding-actions">
          <ProjectSelect projects={projects} value={projectKey} onChange={setProjectKey} label="점검 프로젝트" />
          <Button icon={isLoading || isScanning ? RefreshCw : Play} onClick={handleScan} disabled={!projectKey || isLoading || isScanning}>
            {isScanning ? '점검 진행 중' : isLoading ? '상태 확인 중' : '점검 실행'}
          </Button>
          <Button icon={Download} variant="secondary" onClick={handleExport} disabled={visibleRows.length === 0 || isExporting || isScanning}>
            {isExporting ? 'Excel 생성 중' : 'Excel 다운로드'}
          </Button>
        </div>
        {scanResult && (
          <div className={`secure-coding-job job-${scanResult.status.toLowerCase()}`} role="status">
            <div className="secure-coding-job-message">
              <strong>{scanResult.message}</strong>
              <span>{scanResult.scannedFiles} / {scanResult.totalFiles} 파일 처리</span>
            </div>
            <div className="secure-coding-progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={scanResult.progressPercent}>
              <span style={{ width: `${scanResult.progressPercent}%` }} />
            </div>
            <small>{scanResult.progressPercent}% · 작업 ID #{scanResult.jobId}</small>
          </div>
        )}
      </div>

      {isLoading && !scanResult ? <div className="card secure-coding-loading"><Loading /></div> : scanResult && (
        <>
          <div className="secure-coding-summary">
            <article className="card"><span>대상 파일</span><strong>{scanResult.totalFiles}</strong></article>
            <article className="card passed"><span>통과 파일</span><strong>{scanResult.passedFiles}</strong></article>
            <article className="card finding"><span>취약점</span><strong>{scanResult.findingCount}</strong></article>
            <article className="card error"><span>검사 오류</span><strong>{scanResult.errorFiles}</strong></article>
          </div>

          <div className="card secure-coding-results">
            <div className="page-heading">
              <div>
                <h2>점검 결과</h2>
                <p>{formatDateTime(scanResult.scannedAt || scanResult.requestedAt)} · 전체 {scanResult.results.length}행 중 {visibleRows.length}행 표시</p>
              </div>
            </div>
            <div className="secure-coding-filters">
              <label className="secure-coding-search"><Search size={16} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="파일명, 규칙, 메시지 검색" /></label>
              <select aria-label="상태 필터" value={status} onChange={(event) => setStatus(event.target.value)}>
                <option value="ALL">전체 상태</option>
                <option value="FINDING">취약점</option>
                <option value="PASSED">통과</option>
                <option value="ERROR">검사 오류</option>
              </select>
              <select aria-label="심각도 필터" value={severity} onChange={(event) => setSeverity(event.target.value)}>
                {SEVERITY_OPTIONS.map((item) => <option value={item} key={item}>{item === 'ALL' ? '전체 심각도' : item}</option>)}
              </select>
            </div>
            <div className="secure-coding-table-wrap">
              <table className="secure-coding-table">
                <thead><tr><th>상태</th><th>심각도</th><th>파일</th><th>파일 경로</th><th>유형</th><th>규칙</th><th>위치</th><th>점검 내용</th></tr></thead>
                <tbody>
                  {visibleRows.length === 0 ? (
                    <tr><td className="secure-coding-empty" colSpan="8">조건에 맞는 결과가 없습니다.</td></tr>
                  ) : visibleRows.map((row, index) => (
                    <tr key={`${row.documentId}-${row.ruleId}-${row.startLine ?? 'none'}-${index}`}>
                      <td><span className={`scan-badge status-${row.status.toLowerCase()}`}>{STATUS_LABELS[row.status] ?? row.status}</span></td>
                      <td><span className={`scan-badge severity-${String(row.severity).toLowerCase()}`}>{row.severity}</span></td>
                      <td>
                        <button
                          className="secure-coding-file-button"
                          type="button"
                          onClick={() => openSourceModal(row)}
                          disabled={row.documentId == null}
                          title={row.documentId == null ? '소스 원문이 없습니다.' : '소스 원문 보기'}
                        >
                          <FileCode size={15} aria-hidden="true" />
                          <span>{fileDisplayParts(row.fileName).fileName}</span>
                        </button>
                      </td>
                      <td className="secure-coding-file-path" title={fileDisplayParts(row.fileName).filePath}>
                        <code>{fileDisplayParts(row.fileName).filePath}</code>
                      </td>
                      <td>{row.fileType}</td>
                      <td><code>{row.ruleId}</code></td>
                      <td>{location(row)}</td>
                      <td className="secure-coding-message">{row.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      <Modal
        backdropClassName="java-source-modal-backdrop"
        className="java-source-modal-shell"
        open={sourceModal.open}
        onClose={closeSourceModal}
        title="소스 원문"
      >
        <div className="java-source-modal">
          <div className="java-source-modal-title">
            <FileCode size={18} aria-hidden="true" />
            <span>{sourceModal.row?.fileName}</span>
          </div>
          {sourceModal.row && (
            <div className="java-source-meta">
              <span>{sourceModal.row.fileType}</span>
              {sourceModal.row.startLine != null && (
                <span>점검 위치 {location(sourceModal.row)}</span>
              )}
              {sourceModal.row.ruleId && sourceModal.row.ruleId !== '-' && <span>{sourceModal.row.ruleId}</span>}
            </div>
          )}
          {sourceModal.isLoading ? (
            <div className="java-source-loading">
              <Loading />
              <span>소스 파일을 불러오는 중입니다.</span>
            </div>
          ) : sourceModal.error ? (
            <div className="empty-result java-source-empty"><span>{sourceModal.error}</span></div>
          ) : (
            <SourceCodeViewer
              content={sourceModal.content || '소스 내용이 없습니다.'}
              startLine={sourceModal.row?.startLine}
              endLine={sourceModal.row?.endLine}
            />
          )}
        </div>
      </Modal>
    </section>
  );
}
