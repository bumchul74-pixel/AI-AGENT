import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Database, Network, RefreshCw, Search, Trash2, Waypoints } from 'lucide-react';
import { deleteCleanupTargets, fetchCleanupTargets } from '../api/dataOperationApi.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { formatDateTime } from '../utils/dateUtils.js';

const CONFIRM_TEXT = 'DELETE';

export function DataCleanupPage() {
  const [documents, setDocuments] = useState([]);
  const [selectedIds, setSelectedIds] = useState([]);
  const [query, setQuery] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [progress, setProgress] = useState({ completed: 0, total: 0 });
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const loadMoreLockRef = useRef(false);

  const visibleDocuments = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return documents;
    return documents.filter((document) =>
      [document.originalFileName, document.documentType, document.sourceKey]
        .some((value) => String(value ?? '').toLowerCase().includes(keyword)),
    );
  }, [documents, query]);

  const visibleIds = visibleDocuments.map((document) => document.id);
  const isAllVisibleSelected = visibleIds.length > 0
    && visibleIds.every((id) => selectedIds.includes(id));
  const canDelete = selectedIds.length > 0
    && confirmation === CONFIRM_TEXT
    && !isDeleting;

  async function loadTargets({ page = 0, append = false } = {}) {
    if (append && loadMoreLockRef.current) return;
    if (append) loadMoreLockRef.current = true;
    if (append) {
      setIsLoadingMore(true);
    } else {
      setIsLoading(true);
    }
    setError('');
    try {
      const response = await fetchCleanupTargets({ page, size: 30 });
      setDocuments((current) => {
        if (!append) return response.targets;
        const merged = new Map(current.map((document) => [document.id, document]));
        response.targets.forEach((document) => {
          const previous = merged.get(document.id);
          merged.set(document.id, previous ? {
            ...previous,
            ...document,
            postgresTracked: previous.postgresTracked || document.postgresTracked,
            vectorTracked: previous.vectorTracked || document.vectorTracked,
            graphTracked: previous.graphTracked || document.graphTracked,
          } : document);
        });
        return [...merged.values()];
      });
      setCurrentPage(response.page);
      setTotalCount(response.totalCount);
      setHasNext(response.hasNext);
    } catch (exception) {
      setError(exception.message);
    } finally {
      if (append) {
        setIsLoadingMore(false);
        loadMoreLockRef.current = false;
      } else {
        setIsLoading(false);
      }
    }
  }

  useEffect(() => {
    loadTargets();
  }, []);

  function toggleDocument(id) {
    setSelectedIds((current) => current.includes(id)
      ? current.filter((item) => item !== id)
      : [...current, id]);
    setResult(null);
  }

  function toggleAllVisible() {
    setSelectedIds((current) => isAllVisibleSelected
      ? current.filter((id) => !visibleIds.includes(id))
      : [...new Set([...current, ...visibleIds])]);
    setResult(null);
  }

  function handleTargetScroll(event) {
    const target = event.currentTarget;
    const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
    if (distanceToBottom <= 80 && hasNext && !isLoading && !isLoadingMore && !isDeleting) {
      loadTargets({ page: currentPage + 1, append: true });
    }
  }

  async function handleDelete() {
    if (!canDelete) return;
    const targets = documents.filter((document) => selectedIds.includes(document.id));
    setIsDeleting(true);
    setError('');
    setResult(null);
    setProgress({ completed: 0, total: targets.length });

    try {
      const nextResult = await deleteCleanupTargets(targets, setProgress);
      setResult(nextResult);
      setSelectedIds(nextResult.failures.map((failure) => failure.documentId));
      setConfirmation('');
      await loadTargets();
    } catch (exception) {
      setError(exception.message);
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <section className="data-cleanup-page">
      <section className="card cleanup-intro-panel">
        <div className="cleanup-heading">
          <span className="cleanup-heading-icon"><Trash2 size={22} /></span>
          <div>
            <span className="eyebrow">DESTRUCTIVE DATA OPERATION</span>
            <h1>통합 데이터 삭제</h1>
            <p>색인 소스를 기준으로 PostgreSQL, VectorDB, Neo4j 데이터를 함께 삭제합니다.</p>
          </div>
        </div>
        <div className="cleanup-store-grid">
          <div><Database size={18} /><strong>PostgreSQL</strong><span>문서 메타데이터 논리 삭제</span></div>
          <div><Waypoints size={18} /><strong>VectorDB</strong><span>sourceKey 기반 Chunk 삭제</span></div>
          <div><Network size={18} /><strong>Neo4j</strong><span>graphKey 기반 노드·관계 삭제</span></div>
        </div>
      </section>

      <section className="card cleanup-target-panel">
        <div className="page-heading cleanup-toolbar">
          <div>
            <h2>삭제 대상 선택</h2>
            <p>전체 {totalCount}개 중 {documents.length}개 로드 · {selectedIds.length}개 선택</p>
          </div>
          <Button icon={RefreshCw} variant="secondary" onClick={() => loadTargets()} disabled={isLoading || isLoadingMore || isDeleting}>
            새로고침
          </Button>
        </div>

        <label className="cleanup-search">
          <Search size={17} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="파일명, 유형, sourceKey 검색" />
        </label>

        {error && <p className="error-text">{error}</p>}
        {isLoading ? <Loading /> : visibleDocuments.length === 0 ? (
          <div className="empty-result"><strong>삭제할 색인 소스가 없습니다.</strong></div>
        ) : (
          <div className="cleanup-table-wrap" onScroll={handleTargetScroll}>
            <table className="cleanup-table">
              <thead><tr>
                <th><input aria-label="현재 목록 전체 선택" type="checkbox" checked={isAllVisibleSelected} onChange={toggleAllVisible} /></th>
                <th>파일명</th><th>Source Key</th><th>저장소</th><th>Chunk / Node</th><th>등록 일시</th>
              </tr></thead>
              <tbody>{visibleDocuments.map((document) => (
                <tr className={selectedIds.includes(document.id) ? 'selected' : ''} key={document.id}>
                  <td><input aria-label={`${document.originalFileName} 선택`} type="checkbox" checked={selectedIds.includes(document.id)} onChange={() => toggleDocument(document.id)} /></td>
                  <td><strong>{document.originalFileName}</strong></td>
                  <td><code>{document.sourceKey}</code></td>
                  <td>
                    <div className="cleanup-store-statuses">
                      {document.postgresTracked && <span className="cleanup-link-status postgres">PostgreSQL</span>}
                      {document.vectorTracked && <span className="cleanup-link-status vector">VectorDB</span>}
                      {document.graphTracked && <span className="cleanup-link-status graph">Neo4j</span>}
                      {!document.postgresTracked && !document.vectorTracked && !document.graphTracked
                        && <span className="cleanup-link-status orphan">미연계</span>}
                    </div>
                  </td>
                  <td>{`${document.chunkCount ?? 0} / ${document.graphNodeCount ?? 0}`}</td>
                  <td>{document.createdAt ? formatDateTime(document.createdAt) : '-'}</td>
                </tr>
              ))}
              {isLoadingMore && (
                <tr className="cleanup-loading-row"><td colSpan={6}><Loading /></td></tr>
              )}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="card cleanup-confirm-panel">
        <div className="cleanup-warning">
          <AlertTriangle size={20} />
          <div><strong>삭제 후에는 검색 및 그래프 탐색에서 복구할 수 없습니다.</strong><span>선택한 원본 파일도 Object/File Storage에서 함께 제거됩니다.</span></div>
        </div>
        <div className="cleanup-confirm-controls">
          <label className="field cleanup-confirm-field">
            <span>계속하려면 {CONFIRM_TEXT}를 입력하세요.</span>
            <input value={confirmation} onChange={(event) => setConfirmation(event.target.value)} placeholder={CONFIRM_TEXT} disabled={isDeleting} />
          </label>
          <div className="cleanup-confirm-footer">
            <div className="cleanup-feedback" aria-live="polite">
              {isDeleting && <p className="cleanup-progress">{progress.total}개 중 {progress.completed}개 처리 중</p>}
              {result && <p className={result.failures.length ? 'error-text' : 'success-text'}>
                {result.deletedIds.length}개 삭제 완료{result.failures.length ? `, ${result.failures.length}개 실패` : ''}
              </p>}
            </div>
            <Button icon={Trash2} variant="danger" onClick={handleDelete} disabled={!canDelete}>
              선택한 {selectedIds.length}개 통합 삭제
            </Button>
          </div>
        </div>
      </section>
    </section>
  );
}
