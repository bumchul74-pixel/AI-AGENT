import { useEffect, useMemo, useRef, useState } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import { forceCollide } from 'd3-force-3d';
import { Database, Network, Search } from 'lucide-react';
import { fetchNeo4jLabelGraph, fetchNeo4jNodeDetail, fetchNeo4jNodes } from '../api/neo4jExplorerApi.js';
import { isApiRequestCancelledError } from '../api/apiClient.js';
import { DataTable } from '../components/common/DataTable.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { Modal } from '../components/common/Modal.jsx';
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

function useElementSize(ref, refreshKey) {
  const [size, setSize] = useState({ width: 860, height: 620 });

  useEffect(() => {
    if (!ref.current) return undefined;
    const observer = new ResizeObserver(([entry]) => {
      setSize({
        width: Math.max(240, Math.floor(entry.contentRect.width)),
        height: Math.max(420, Math.floor(entry.contentRect.height)),
      });
    });
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [ref, refreshKey]);

  return size;
}

function labelColor(label) {
  const palette = ['#2563eb', '#0f766e', '#7c3aed', '#c2410c', '#be123c', '#0369a1', '#4d7c0f'];
  const hash = Array.from(label ?? '').reduce((value, character) => (
    ((value * 31) + character.charCodeAt(0)) >>> 0
  ), 0);
  return palette[hash % palette.length];
}

function graphEndpointId(endpoint) {
  return typeof endpoint === 'object' ? endpoint?.id : endpoint;
}

function LabelGraphContent({ graph, loading }) {
  const containerRef = useRef(null);
  const forceGraphRef = useRef(null);
  const size = useElementSize(containerRef, loading);
  const [hoveredLinkId, setHoveredLinkId] = useState('');
  const [hoveredNodeId, setHoveredNodeId] = useState('');
  const [selectedLabel, setSelectedLabel] = useState('');
  const graphData = useMemo(() => {
    const relationships = graph?.relationships ?? [];
    const directedPairCounts = relationships.reduce((counts, relationship) => {
      const key = `${relationship.source}\u0000${relationship.target}`;
      counts.set(key, (counts.get(key) ?? 0) + 1);
      return counts;
    }, new Map());
    const directedPairPositions = new Map();

    return {
      nodes: (graph?.nodes ?? []).map((node) => ({
        id: node.label,
        label: node.label,
        nodeCount: node.nodeCount,
      })),
      links: relationships.map((relationship, index) => {
        const key = `${relationship.source}\u0000${relationship.target}`;
        const reverseKey = `${relationship.target}\u0000${relationship.source}`;
        const pairCount = directedPairCounts.get(key) ?? 1;
        const pairPosition = directedPairPositions.get(key) ?? 0;
        directedPairPositions.set(key, pairPosition + 1);
        const hasReverseDirection = relationship.source !== relationship.target
          && directedPairCounts.has(reverseKey);
        let curvature = 0;
        if (relationship.source === relationship.target) {
          curvature = 0.35 + pairPosition * 0.12;
        } else if (pairCount > 1) {
          curvature = (pairPosition - (pairCount - 1) / 2) * 0.18 + (hasReverseDirection ? 0.12 : 0);
        } else if (hasReverseDirection) {
          curvature = 0.18;
        }

        return {
          id: `${relationship.source}:${relationship.type}:${relationship.target}:${index}`,
          source: relationship.source,
          target: relationship.target,
          type: relationship.type,
          relationshipCount: relationship.relationshipCount,
          curvature,
        };
      }),
    };
  }, [graph]);
  const selectedRelationships = useMemo(() => {
    if (!selectedLabel) return [];
    return graphData.links
      .filter((link) => (
        graphEndpointId(link.source) === selectedLabel || graphEndpointId(link.target) === selectedLabel
      ))
      .sort((left, right) => right.relationshipCount - left.relationshipCount
        || left.type.localeCompare(right.type));
  }, [graphData, selectedLabel]);
  const connectedLabels = useMemo(() => new Set(selectedRelationships.flatMap((link) => [
    graphEndpointId(link.source),
    graphEndpointId(link.target),
  ])), [selectedRelationships]);
  const orderedNodes = useMemo(() => [...graphData.nodes].sort((left, right) => (
    right.nodeCount - left.nodeCount || left.label.localeCompare(right.label)
  )), [graphData.nodes]);
  const selectedNode = graphData.nodes.find((node) => node.id === selectedLabel);

  useEffect(() => {
    const graphInstance = forceGraphRef.current;
    if (!graphInstance || loading || graphData.nodes.length === 0) return undefined;

    const chargeStrength = -Math.min(1800, Math.max(650, graphData.nodes.length * 24));
    const linkDistance = Math.min(260, Math.max(170, 130 + Math.sqrt(graphData.nodes.length) * 12));
    graphInstance.d3Force('charge')?.strength(chargeStrength).distanceMax(1400);
    graphInstance.d3Force('link')?.distance(linkDistance).strength(0.12);
    graphInstance.d3Force(
      'collision',
      forceCollide()
        .radius((node) => Math.max(44, Math.min(110, (node.label?.length ?? 0) * 4.2 + 30)))
        .strength(1)
        .iterations(3),
    );
    graphInstance.d3ReheatSimulation();

    return () => graphInstance.d3Force('collision', null);
  }, [graphData, loading]);

  if (loading) return <div className="neo4j-schema-loading"><Loading /></div>;
  if (!graphData.nodes.length) {
    return (
      <div className="neo4j-schema-empty">
        <Network size={34} />
        <strong>표시할 라벨이 없습니다.</strong>
        <span>Neo4j에 노드와 관계를 적재한 뒤 다시 확인해 주세요.</span>
      </div>
    );
  }

  return (
    <div className="neo4j-schema-content">
      <div className="neo4j-schema-summary">
        <span><strong>{graphData.nodes.length.toLocaleString()}</strong> 라벨</span>
        <span><strong>{graphData.links.length.toLocaleString()}</strong> 관계 유형</span>
        {selectedLabel && (
          <button type="button" onClick={() => setSelectedLabel('')}>전체 관계 보기</button>
        )}
        {(graph?.labelsTruncated || graph?.relationshipsTruncated) && (
          <span className="neo4j-schema-warning">조회 한도에 따라 일부 집계만 표시합니다.</span>
        )}
      </div>
      <div className="neo4j-schema-workbench">
        <div className="neo4j-schema-canvas" ref={containerRef}>
        <ForceGraph2D
          ref={forceGraphRef}
          width={size.width}
          height={size.height}
          graphData={graphData}
          nodeLabel={(node) => `${node.label} · ${node.nodeCount.toLocaleString()} nodes`}
          nodeColor={(node) => {
            if (!selectedLabel || node.id === selectedLabel || connectedLabels.has(node.id)) {
              return labelColor(node.label);
            }
            return '#cbd5e1';
          }}
          nodeVal={(node) => Math.max(2, Math.log2(node.nodeCount + 1))}
          nodeRelSize={6}
          linkColor={(link) => {
            const focused = !selectedLabel
              || graphEndpointId(link.source) === selectedLabel
              || graphEndpointId(link.target) === selectedLabel;
            if (link.id === hoveredLinkId) return '#2563eb';
            return focused ? 'rgba(71, 85, 105, 0.62)' : 'rgba(148, 163, 184, 0.1)';
          }}
          linkWidth={(link) => {
            const focused = !selectedLabel
              || graphEndpointId(link.source) === selectedLabel
              || graphEndpointId(link.target) === selectedLabel;
            if (link.id === hoveredLinkId) return Math.max(2.5, Math.log2(link.relationshipCount + 1));
            return focused ? Math.max(0.8, Math.log2(link.relationshipCount + 1) * 0.55) : 0.35;
          }}
          linkDirectionalArrowLength={5}
          linkDirectionalArrowRelPos={0.92}
          linkCurvature={(link) => link.curvature}
          linkLabel={(link) => `${link.type} · ${link.relationshipCount.toLocaleString()}`}
          linkCanvasObjectMode={() => 'after'}
          linkCanvasObject={(link, context, globalScale) => {
            if (!link.source || !link.target || typeof link.source !== 'object' || typeof link.target !== 'object') return;
            const isHovered = link.id === hoveredLinkId;
            if (!isHovered) return;

            const sourceX = link.source.x ?? 0;
            const sourceY = link.source.y ?? 0;
            const targetX = link.target.x ?? 0;
            const targetY = link.target.y ?? 0;
            const deltaX = targetX - sourceX;
            const deltaY = targetY - sourceY;
            const distance = Math.max(1, Math.hypot(deltaX, deltaY));
            const curveOffset = link.source.id === link.target.id
              ? 38 + link.curvature * 18
              : link.curvature * distance * 0.55;
            const x = (sourceX + targetX) / 2 - (deltaY / distance) * curveOffset;
            const y = (sourceY + targetY) / 2 + (deltaX / distance) * curveOffset;
            const fontSize = isHovered ? 11 / globalScale : 10;
            const padding = isHovered ? 4 / globalScale : 3;
            const text = `${link.type} (${link.relationshipCount})`;
            context.font = `700 ${fontSize}px sans-serif`;
            const width = context.measureText(text).width;
            context.fillStyle = isHovered ? 'rgba(219, 234, 254, 0.96)' : 'rgba(255, 255, 255, 0.9)';
            context.fillRect(x - width / 2 - padding, y - fontSize / 2 - padding, width + padding * 2, fontSize + padding * 2);
            context.fillStyle = isHovered ? '#1d4ed8' : '#475569';
            context.textAlign = 'center';
            context.textBaseline = 'middle';
            context.fillText(text, x, y);
          }}
          nodeCanvasObjectMode={() => 'after'}
          nodeCanvasObject={(node, context, globalScale) => {
            const isHovered = node.id === hoveredNodeId;
            const isRelevant = !selectedLabel || node.id === selectedLabel || connectedLabels.has(node.id);
            const labelScaleThreshold = graphData.nodes.length > 50 ? 1.1 : 0.7;
            if (!isHovered && (!isRelevant || (!selectedLabel && globalScale < labelScaleThreshold))) return;
            const fontSize = isHovered ? 11 / globalScale : 10;
            const padding = isHovered ? 4 / globalScale : 3;
            context.font = `700 ${fontSize}px sans-serif`;
            const width = context.measureText(node.label).width;
            const y = (node.y ?? 0) + (isHovered ? 10 / globalScale : 9);
            context.fillStyle = isHovered ? 'rgba(219, 234, 254, 0.97)' : 'rgba(255, 255, 255, 0.92)';
            context.fillRect((node.x ?? 0) - width / 2 - padding, y, width + padding * 2, fontSize + padding * 2);
            context.fillStyle = isHovered ? '#1d4ed8' : '#1e293b';
            context.textAlign = 'center';
            context.textBaseline = 'top';
            context.fillText(node.label, node.x ?? 0, y + padding);
          }}
          d3AlphaDecay={0.025}
          d3VelocityDecay={0.25}
          cooldownTicks={220}
          onNodeHover={(node) => setHoveredNodeId(node?.id ?? '')}
          onNodeClick={(node) => setSelectedLabel(node.id)}
          onLinkHover={(link) => setHoveredLinkId(link?.id ?? '')}
          onEngineStop={() => forceGraphRef.current?.zoomToFit?.(600, 100)}
        />
        </div>
        <aside className="neo4j-schema-inspector" aria-live="polite">
          {selectedNode ? (
            <>
              <div className="neo4j-schema-inspector-header">
                <div>
                  <span>선택한 라벨</span>
                  <strong>{selectedNode.label}</strong>
                  <small>{selectedNode.nodeCount.toLocaleString()}개 노드</small>
                </div>
                <button type="button" onClick={() => setSelectedLabel('')}>전체</button>
              </div>
              <div className="neo4j-schema-relationship-summary">
                연결된 관계 유형 {selectedRelationships.length.toLocaleString()}개
              </div>
              <div className="neo4j-schema-relationship-details">
                {selectedRelationships.length ? selectedRelationships.map((link) => {
                  const sourceId = graphEndpointId(link.source);
                  const targetId = graphEndpointId(link.target);
                  const selfRelationship = sourceId === targetId;
                  const outgoing = sourceId === selectedLabel;
                  const otherLabel = selfRelationship ? selectedLabel : outgoing ? targetId : sourceId;
                  return (
                    <article key={link.id}>
                      <span className="neo4j-schema-direction">
                        {selfRelationship ? '자기 관계' : outgoing ? '나감' : '들어옴'}
                      </span>
                      <strong>{link.type}</strong>
                      <span>{link.relationshipCount.toLocaleString()}건</span>
                      <button
                        type="button"
                        disabled={selfRelationship}
                        onClick={() => setSelectedLabel(otherLabel)}
                      >
                        {otherLabel}
                      </button>
                    </article>
                  );
                }) : <p>연결된 관계가 없습니다.</p>}
              </div>
            </>
          ) : (
            <>
              <div className="neo4j-schema-inspector-header">
                <div>
                  <span>라벨 탐색</span>
                  <strong>관계를 확인할 라벨을 선택하세요.</strong>
                  <small>그래프의 노드 또는 아래 목록을 선택할 수 있습니다.</small>
                </div>
              </div>
              <div className="neo4j-schema-label-options">
                {orderedNodes.map((node) => (
                  <button key={node.id} type="button" onClick={() => setSelectedLabel(node.id)}>
                    <i style={{ backgroundColor: labelColor(node.label) }} />
                    <span>{node.label}</span>
                    <strong>{node.nodeCount.toLocaleString()}</strong>
                  </button>
                ))}
              </div>
            </>
          )}
        </aside>
      </div>
      <p className="neo4j-schema-hint">전체 구조를 먼저 확인한 뒤 라벨을 선택하면 관련 노드와 관계만 강조됩니다. 관계 타입과 건수는 우측 패널에서 확인할 수 있습니다.</p>
    </div>
  );
}

function DetailContent({ detail, loading, onSelectNode }) {
  if (loading) return <div className="neo4j-detail-loading"><Loading /></div>;
  if (!detail) {
    return (
      <div className="neo4j-detail-empty">
        <Database size={30} />
        <strong>노드 상세 정보를 불러오지 못했습니다.</strong>
      </div>
    );
  }

  const properties = Object.entries(detail.properties ?? {});
  return (
    <div className="neo4j-detail-content" aria-live="polite">
      <div className="neo4j-detail-summary">
        <code>{detail.elementId}</code>
        <Labels labels={detail.labels} />
      </div>

      <section className="neo4j-detail-section">
        <h3>속성 <span>{properties.length}</span></h3>
        {properties.length ? (
          <div className="neo4j-property-list">
            {properties.map(([key, value]) => (
              <div key={key}><dt>{key}</dt><dd><Value value={value} /></dd></div>
            ))}
          </div>
        ) : <p className="neo4j-section-empty">저장된 속성이 없습니다.</p>}
      </section>

      <section className="neo4j-detail-section">
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
      </section>
    </div>
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
  const [labelGraphOpen, setLabelGraphOpen] = useState(false);
  const [labelGraph, setLabelGraph] = useState(null);
  const [labelGraphLoading, setLabelGraphLoading] = useState(false);
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

  useEffect(() => {
    if (!labelGraphOpen) return undefined;
    const controller = new AbortController();
    setLabelGraph(null);
    setLabelGraphLoading(true);
    fetchNeo4jLabelGraph(controller.signal)
      .then(setLabelGraph)
      .catch((error) => {
        if (!isApiRequestCancelledError(error)) setLabelGraph(null);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLabelGraphLoading(false);
      });
    return () => controller.abort();
  }, [labelGraphOpen]);

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

  function openDetail(elementId) {
    setDetail(null);
    setDetailLoading(true);
    setSelectedElementId(elementId);
  }

  function closeDetail() {
    setSelectedElementId('');
    setDetail(null);
    setDetailLoading(false);
  }

  function closeLabelGraph() {
    setLabelGraphOpen(false);
    setLabelGraph(null);
    setLabelGraphLoading(false);
  }

  function search(event) {
    event.preventDefault();
    closeDetail();
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
        <div className="neo4j-hero-actions">
          <button className="neo4j-schema-button" type="button" onClick={() => setLabelGraphOpen(true)}>
            <Network size={17} />
            라벨 관계 그래프
          </button>
          <div className="neo4j-total"><strong>{pageData.totalElements.toLocaleString()}</strong><span>전체 노드</span></div>
        </div>
      </section>

      <form className="card neo4j-filter-bar" onSubmit={search}>
        <label><span>라벨</span><input value={draftLabel} onChange={(event) => setDraftLabel(event.target.value)} placeholder="Method" /></label>
        <label><span>검색어</span><input value={draftKeyword} onChange={(event) => setDraftKeyword(event.target.value)} placeholder="라벨과 모든 속성에서 검색" /></label>
        <button className="neo4j-search-button" type="submit"><Search size={16} />검색</button>
      </form>

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
                    <td><button type="button" onClick={() => openDetail(node.elementId)}><strong>{node.displayName || node.elementId}</strong><code>{node.elementId}</code></button></td>
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

      <Modal
        className="neo4j-detail-modal"
        open={Boolean(selectedElementId)}
        onClose={closeDetail}
        title={detail?.displayName || '노드 상세'}
      >
        <DetailContent detail={detail} loading={detailLoading} onSelectNode={openDetail} />
      </Modal>

      <Modal
        className="neo4j-schema-modal"
        open={labelGraphOpen}
        onClose={closeLabelGraph}
        title="라벨 관계 그래프"
      >
        <LabelGraphContent graph={labelGraph} loading={labelGraphLoading} />
      </Modal>
    </div>
  );
}