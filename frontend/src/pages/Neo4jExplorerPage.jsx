import { useEffect, useRef, useState } from 'react';
import { Database, Search } from 'lucide-react';
import { fetchNeo4jNodeDetail, fetchNeo4jNodes } from '../api/neo4jExplorerApi.js';
import { isApiRequestCancelledError } from '../api/apiClient.js';
import { DataTable } from '../components/common/DataTable.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { ScrollableListLayout } from '../components/common/ScrollableListLayout.jsx';

const NODE_PAGE_SIZE = 30;
const LOAD_MORE_THRESHOLD_PX = 80;
const EMPTY_PAGE = {
  content: [], page: 0, size: NODE_PAGE_SIZE, totalElements: 0, totalPages: 0, first: true, last: true,
};

function appendUniqueNodes(currentNodes, nextNodes) {
  const nodesById = new Map(currentNodes.map((node) => [node.elementId, node]));
  nextNodes.forEach((node) => nodesById.set(node.elementId, node));
  return Array.from(nodesById.values());
}

function Value({ value }) {
  if (value === null || value === undefined) return <span className="neo4j-empty-value">null</span>;
  if (typeof value === 'object') return <pre>{JSON.stringify(value, null, 2)}</pre>;
  return <span>{String(value)}</span>;
}

function Labels({ labels = [] }) {
  if (!labels.length) return <span className="neo4j-empty-value">라벨 없음</span>;
  return <span className="neo4j-label-list">{labels.map((label) => <span key={label}>{label}</span>)}</span>;
}

function DetailPanel({ detail, loading, onSelectNode }) {
  if (loading) return <section className="card neo4j-detail-panel"><Loading /></section>;
  if (!detail) {
    return (
      <section className="card neo4j-detail-panel neo4j-detail-empty">
        <Database size={30} />
        <strong>노드를 선택하세요.</strong>
        <p>목록에서 노드를 선택하면 속성과 관계를 확인할 수 있습니다.</p>
      </section>
    );
  }

  const properties = Object.entries(detail.properties ?? {});
  return (
    <section className="card neo4j-detail-panel" aria-live="polite">
      <header className="neo4j-detail-header">
        <div>
          <p className="eyebrow">NODE DETAIL</p>
          <h2>{detail.displayName || detail.elementId}</h2>
          <code>{detail.elementId}</code>
        </div>
        <Labels labels={detail.labels} />
      </header>

      <div className="neo4j-detail-section">
        <h3>속성 <span>{properties.length}</span></h3>
        {properties.length ? (
          <div className="neo4j-property-list">
            {properties.map(([key, value]) => (
              <div key={key}><dt>{key}</dt><dd><Value value={value} /></dd></div>
            ))}
          </div>
        ) : <p className="neo4j-section-empty">저장된 속성이 없습니다.</p>}
      </div>

      <div className="neo4j-detail-section">
        <h3>관계 <span>{detail.relationshipCount ?? 0}</span></h3>
        {(detail.relationships ?? []).length ? (
          <div className="neo4j-relationship-list">
            {detail.relationships.map((relationship) => (
              <article key={relationship.elementId}>
                <div className="neo4j-relationship-heading">
                  <span className={`neo4j-direction ${relationship.direction?.toLowerCase()}`}>
                    {relationship.direction === 'OUTGOING' ? '나감' : '들어옴'}
                  </span>
                  <strong>{relationship.type}</strong>
                </div>
                <button type="button" onClick={() => onSelectNode(relationship.otherElementId)}>
                  {relationship.otherDisplayName || relationship.otherElementId}
                </button>
                <Labels labels={relationship.otherLabels} />
                {Object.keys(relationship.properties ?? {}).length > 0 && (
                  <pre>{JSON.stringify(relationship.properties, null, 2)}</pre>
                )}
              </article>
            ))}
          </div>
        ) : <p className="neo4j-section-empty">연결된 관계가 없습니다.</p>}
        {(detail.relationshipCount ?? 0) > (detail.relationships?.length ?? 0) && (
          <p className="neo4j-limit-note">최대 500개까지 표시합니다.</p>
        )}
      </div>
    </section>
  );
}

export function Neo4jExplorerPage() {
  const [draftLabel, setDraftLabel] = useState('');
  const [draftKeyword, setDraftKeyword] = useState('');
  const [filters, setFilters] = useState({ label: '', keyword: '' });
  const [searchVersion, setSearchVersion] = useState(0);
  const [nodes, setNodes] = useState([]);
  const [pageData, setPageData] = useState(EMPTY_PAGE);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selectedElementId, setSelectedElementId] = useState('');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const loadMoreLockRef = useRef(false);
  const loadMoreControllerRef = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    loadMoreControllerRef.current?.abort();
    loadMoreLockRef.current = false;
    setLoadingMore(false);
    setLoading(true);
    setNodes([]);
    setPageData(EMPTY_PAGE);
    fetchNeo4jNodes({ ...filters, page: 0, size: NODE_PAGE_SIZE }, controller.signal)
      .then((result) => {
        const nextPage = result ?? EMPTY_PAGE;
        setNodes(nextPage.content ?? []);
        setPageData(nextPage);
      })
      .catch((error) => {
        if (!isApiRequestCancelledError(error)) {
          setNodes([]);
          setPageData(EMPTY_PAGE);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [filters, searchVersion]);

  useEffect(() => {
    if (!selectedElementId) {
      setDetail(null);
      return undefined;
    }
    const controller = new AbortController();
    setDetailLoading(true);
    fetchNeo4jNodeDetail(selectedElementId, controller.signal)
      .then(setDetail)
      .catch((error) => {
        if (!isApiRequestCancelledError(error)) setDetail(null);
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false);
      });
    return () => controller.abort();
  }, [selectedElementId]);

  useEffect(() => () => loadMoreControllerRef.current?.abort(), []);

  async function loadMoreNodes() {
    if (loading || loadingMore || loadMoreLockRef.current || pageData.last) return;
    loadMoreLockRef.current = true;
    setLoadingMore(true);
    const controller = new AbortController();
    loadMoreControllerRef.current = controller;
    const nextPageNumber = pageData.page + 1;
    try {
      const nextPage = await fetchNeo4jNodes(
        { ...filters, page: nextPageNumber, size: NODE_PAGE_SIZE },
        controller.signal,
      );
      setNodes((current) => appendUniqueNodes(current, nextPage?.content ?? []));
      setPageData(nextPage ?? pageData);
    } catch (error) {
      if (!isApiRequestCancelledError(error)) return;
    } finally {
      if (loadMoreControllerRef.current === controller) loadMoreControllerRef.current = null;
      loadMoreLockRef.current = false;
      if (!controller.signal.aborted) setLoadingMore(false);
    }
  }

  function handleNodeScroll(event) {
    const target = event.currentTarget;
    const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
    if (distanceToBottom <= LOAD_MORE_THRESHOLD_PX) loadMoreNodes();
  }

  function search(event) {
    event.preventDefault();
    setSelectedElementId('');
    setFilters({ label: draftLabel.trim(), keyword: draftKeyword.trim() });
    setSearchVersion((current) => current + 1);
  }

  return (
    <div className="neo4j-explorer-page">
      <section className="card neo4j-explorer-hero">
        <div>
          <p className="eyebrow">NEO4J DATA EXPLORER</p>
          <h1>Neo4j 데이터 탐색기</h1>
          <p>Java Graph와 독립된 읽기 전용 화면에서 전체 노드와 상세 관계를 조회합니다.</p>
        </div>
        <div className="neo4j-total"><strong>{pageData.totalElements.toLocaleString()}</strong><span>전체 노드</span></div>
      </section>

      <form className="card neo4j-filter-bar" onSubmit={search}>
        <label><span>라벨</span><input value={draftLabel} onChange={(event) => setDraftLabel(event.target.value)} placeholder="Method" /></label>
        <label><span>검색어</span><input value={draftKeyword} onChange={(event) => setDraftKeyword(event.target.value)} placeholder="라벨과 모든 속성에서 검색" /></label>
        <button className="neo4j-search-button" type="submit"><Search size={16} />검색</button>
      </form>

      <div className="neo4j-explorer-grid">
        <ScrollableListLayout
          className="neo4j-list-panel"
          scrollClassName="neo4j-table-wrap"
          ariaLabel="Neo4j 노드 목록"
          onScroll={handleNodeScroll}
          footer={(
            <>
              <span>전체 {pageData.totalElements.toLocaleString()}건 중 {nodes.length.toLocaleString()}건 표시</span>
              <span>{pageData.last ? '모든 데이터를 불러왔습니다.' : '목록 아래로 이동하면 다음 30건을 불러옵니다.'}</span>
            </>
          )}
        >
          {loading ? <Loading /> : (
            <>
              <DataTable
                className="neo4j-node-table"
                columns={[
                  { key: 'node', header: '노드' },
                  { key: 'labels', header: '라벨' },
                  { key: 'properties', header: '속성' },
                  { key: 'relationships', header: '관계' },
                ]}
                rows={nodes}
                rowKey={(node) => node.elementId}
                rowClassName={(node) => selectedElementId === node.elementId ? 'selected' : ''}
                renderCells={(node) => (
                  <>
                    <td><button type="button" onClick={() => setSelectedElementId(node.elementId)}><strong>{node.displayName || node.elementId}</strong><code>{node.elementId}</code></button></td>
                    <td><Labels labels={node.labels} /></td><td>{node.propertyCount}</td><td>{node.relationshipCount}</td>
                  </>
                )}
              />
              {!nodes.length && <div className="neo4j-list-empty">조회된 노드가 없습니다.</div>}
              {loadingMore && <div className="neo4j-load-more-status">다음 데이터를 불러오는 중입니다.</div>}
              {!pageData.last && !loadingMore && nodes.length > 0 && (
                <button className="neo4j-load-more-button" type="button" onClick={loadMoreNodes}>다음 데이터 불러오기</button>
              )}
            </>
          )}
        </ScrollableListLayout>
        <DetailPanel detail={detail} loading={detailLoading} onSelectNode={setSelectedElementId} />
      </div>
    </div>
  );
}