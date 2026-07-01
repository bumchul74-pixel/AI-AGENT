import { useEffect, useMemo, useRef, useState } from 'react';
import { forceCollide, forceX, forceY } from 'd3-force-3d';
import ForceGraph2D from 'react-force-graph-2d';
import { Network, RotateCcw, Search } from 'lucide-react';
import { fetchSourceGraphOverview } from '../api/sourceGraphApi.js';
import { Loading } from '../components/common/Loading.jsx';

const TEXT = {
  title: 'Java Graph',
  description: 'Neo4j\uC5D0 \uC800\uC7A5\uB41C Java \uC18C\uC2A4 \uAD00\uACC4\uB97C \uB178\uB4DC \uADF8\uB798\uD504\uB85C \uC870\uD68C\uD569\uB2C8\uB2E4.',
  searchLabel: '\uAC80\uC0C9\uC5B4',
  searchPlaceholder: 'FQN, \uD30C\uC77C\uBA85, \uB178\uB4DC \uC720\uD615, source',
  search: '\uAC80\uC0C9',
  reset: '\uCD08\uAE30\uD654',
  graphTitle: '\uADF8\uB798\uD504 \uAD6C\uC870',
  detailTitle: '\uB178\uB4DC \uC0C1\uC138',
  emptyTitle: '\uD45C\uC2DC\uD560 Graph \uB370\uC774\uD130\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.',
  emptyDescription: 'inbox\uC5D0 Java \uC18C\uC2A4\uB97C \uC801\uC7AC\uD558\uAC70\uB098 Generate \uC774\uB825\uC744 \uC0DD\uC131\uD574 \uC8FC\uC138\uC694.',
  noSelection: '\uADF8\uB798\uD504\uC758 \uB178\uB4DC\uB97C \uD074\uB9AD\uD558\uBA74 \uC0C1\uC138 \uC815\uBCF4\uAC00 \uD45C\uC2DC\uB429\uB2C8\uB2E4.',
  nodes: 'Nodes',
  links: 'Links',
  label: 'Label',
  id: 'ID',
  name: 'Name',
  properties: 'Properties',
};

const NODE_COLORS = {
  Generation: '#2563eb',
  RagSource: '#0f766e',
  SourceFile: '#b45309',
  JavaType: '#15803d',
  Method: '#7c3aed',
};

const NODE_LEGEND = [
  { label: 'Generation', meaning: '\uC0DD\uC131 \uC774\uB825', color: NODE_COLORS.Generation },
  { label: 'RagSource', meaning: 'RAG \uC18C\uC2A4', color: NODE_COLORS.RagSource },
  { label: 'SourceFile', meaning: 'Java \uD30C\uC77C', color: NODE_COLORS.SourceFile },
  { label: 'JavaType', meaning: '\uD074\uB798\uC2A4/\uC778\uD130\uD398\uC774\uC2A4', color: NODE_COLORS.JavaType },
  { label: 'Method', meaning: '\uBA54\uC11C\uB4DC', color: NODE_COLORS.Method },
];

const NODE_RADIUS_BY_LABEL = {
  Generation: 38,
  RagSource: 38,
  SourceFile: 36,
  JavaType: 36,
  Method: 32,
};

function graphNodeName(node) {
  return node.name || node.properties?.simpleName || node.properties?.fileName || node.id;
}

function graphNodeColor(node) {
  return NODE_COLORS[node.label] ?? '#64748b';
}

function graphNodeDisplayName(node) {
  const name = graphNodeName(node);
  if (node.properties?.simpleName) {
    return node.properties.simpleName;
  }
  if (node.properties?.fileName) {
    return node.properties.fileName.replace(/\.java$/i, '');
  }
  return name.split('.').pop() || name;
}

function graphNodeTextLines(node) {
  const label = graphNodeDisplayName(node);

  if (label.length <= 10) {
    return [label];
  }

  if (label.length <= 18) {
    return [label.slice(0, 9), label.slice(9)];
  }

  return [label.slice(0, 9), `${label.slice(9, 17)}..`];
}

function estimateGraphNodeRadius(node) {
  const baseRadius = NODE_RADIUS_BY_LABEL[node.label] ?? 34;
  const lines = graphNodeTextLines(node);
  const longestLine = Math.max(...lines.map((line) => line.length));
  const textRadius = longestLine * 4.6 + 18;
  const lineRadius = lines.length > 1 ? 40 : 32;

  return Math.ceil(Math.max(baseRadius, textRadius, lineRadius));
}

function graphLinkNodeId(node) {
  return typeof node === 'object' && node !== null ? node.id : node;
}

function graphLinkDistance(link) {
  const sourceRadius = typeof link.source === 'object' && link.source ? estimateGraphNodeRadius(link.source) : 36;
  const targetRadius = typeof link.target === 'object' && link.target ? estimateGraphNodeRadius(link.target) : 36;
  return sourceRadius + targetRadius + 150;
}

function transformGraph(graph) {
  const nodes = graph.nodes.map((node) => ({
    id: node.id,
    label: node.label,
    name: node.name,
    properties: node.properties ?? {},
  }));
  const links = graph.relationships.map((relationship, index) => ({
    id: `${relationship.sourceId}-${relationship.type}-${relationship.targetId}-${index}`,
    source: relationship.sourceId,
    target: relationship.targetId,
    type: relationship.type,
    properties: relationship.properties ?? {},
  }));

  return { nodes, links };
}

function useElementSize(ref) {
  const [size, setSize] = useState({ width: 1200, height: 820 });

  useEffect(() => {
    if (!ref.current) {
      return undefined;
    }

    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      setSize({
        width: Math.max(320, Math.floor(width)),
        height: Math.max(640, Math.floor(height)),
      });
    });
    observer.observe(ref.current);

    return () => observer.disconnect();
  }, [ref]);

  return size;
}

export function JavaGraphPage() {
  const graphRef = useRef(null);
  const forceGraphRef = useRef(null);
  const graphSize = useElementSize(graphRef);
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [graph, setGraph] = useState({ nodes: [], relationships: [] });
  const [selectedNode, setSelectedNode] = useState(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const graphData = useMemo(() => transformGraph(graph), [graph]);

  async function loadGraph(nextQuery = submittedQuery) {
    setIsLoading(true);
    setError('');

    try {
      const result = await fetchSourceGraphOverview({ query: nextQuery, limit: 600 });
      setGraph(result);
      setSelectedNode(null);
    } catch (exception) {
      setGraph({ nodes: [], relationships: [] });
      setSelectedNode(null);
      setError(exception.message);
    } finally {
      setIsLoading(false);
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    const nextQuery = query.trim();
    setSubmittedQuery(nextQuery);
    loadGraph(nextQuery);
  }

  function handleReset() {
    setQuery('');
    setSubmittedQuery('');
    loadGraph('');
  }

  useEffect(() => {
    loadGraph('');
  }, []);

  useEffect(() => {
    if (!forceGraphRef.current || graphData.nodes.length === 0) {
      return undefined;
    }

    const graphInstance = forceGraphRef.current;
    const linkForce = graphInstance.d3Force('link');
    const chargeForce = graphInstance.d3Force('charge');

    linkForce?.distance(graphLinkDistance).strength(0.18);
    chargeForce?.strength(-900).distanceMin(120).distanceMax(1200);
    graphInstance.d3Force('collide', forceCollide((node) => estimateGraphNodeRadius(node) + 30).strength(1).iterations(4));
    graphInstance.d3Force('x', forceX(0).strength(0.018));
    graphInstance.d3Force('y', forceY(0).strength(0.018));
    graphInstance.d3ReheatSimulation?.();

    const timer = window.setTimeout(() => {
      graphInstance.zoomToFit?.(700, 90);
    }, 900);

    return () => window.clearTimeout(timer);
  }, [graphData, graphSize.width, graphSize.height]);

  return (
    <section className="java-graph-page">
      <section className="card java-graph-search-panel">
        <div className="panel-title">
          <Network size={18} />
          <div>
            <h1>{TEXT.title}</h1>
            <p>{TEXT.description}</p>
          </div>
        </div>

        <form className="java-graph-search-form" onSubmit={handleSubmit}>
          <label className="field java-graph-search-field">
            <span>{TEXT.searchLabel}</span>
            <input
              value={query}
              placeholder={TEXT.searchPlaceholder}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>

          <div className="java-graph-search-actions">
            <button className="button primary" type="submit" disabled={isLoading}>
              <Search size={17} aria-hidden="true" />
              <span>{TEXT.search}</span>
            </button>
            <button className="button secondary" type="button" onClick={handleReset} disabled={isLoading}>
              <RotateCcw size={17} aria-hidden="true" />
              <span>{TEXT.reset}</span>
            </button>
          </div>
        </form>
      </section>

      <section className="java-graph-content-grid">
        <section className="card java-graph-panel">
          <div className="java-graph-toolbar">
            <div className="panel-title">
              <Network size={18} />
              <div>
                <h2>{TEXT.graphTitle}</h2>
                <p>{submittedQuery ? submittedQuery : 'All Neo4j graph data'}</p>
              </div>
            </div>
            <div className="java-graph-toolbar-side">
              <div className="java-graph-stats">
                <span>{TEXT.nodes}: <strong>{graphData.nodes.length}</strong></span>
                <span>{TEXT.links}: <strong>{graphData.links.length}</strong></span>
              </div>
              <div className="java-graph-legend" aria-label="Node color legend">
                {NODE_LEGEND.map((item) => (
                  <span className="java-graph-legend-item" key={item.label}>
                    <i style={{ backgroundColor: item.color }} aria-hidden="true" />
                    <strong>{item.label}</strong>
                    <em>{item.meaning}</em>
                  </span>
                ))}
              </div>
            </div>
          </div>

          {error && <p className="error-text">{error}</p>}

          {isLoading ? (
            <Loading />
          ) : graphData.nodes.length === 0 ? (
            <div className="empty-result">
              <strong>{TEXT.emptyTitle}</strong>
              <span>{TEXT.emptyDescription}</span>
            </div>
          ) : (
            <div className="java-graph-canvas" ref={graphRef}>
              <ForceGraph2D
                ref={forceGraphRef}
                width={graphSize.width}
                height={graphSize.height}
                graphData={graphData}
                nodeLabel={(node) => `${node.label}: ${graphNodeName(node)}`}
                nodeColor={graphNodeColor}
                nodeRelSize={11}
                nodeVal={(node) => Math.max(10, estimateGraphNodeRadius(node) / 3)}
                d3AlphaDecay={0.018}
                d3VelocityDecay={0.28}
                linkDistance={graphLinkDistance}
                linkWidth={(link) => {
                  const sourceId = graphLinkNodeId(link.source);
                  const targetId = graphLinkNodeId(link.target);
                  return selectedNode && (sourceId === selectedNode.id || targetId === selectedNode.id) ? 2.4 : 1.1;
                }}
                linkColor={(link) => {
                  const sourceId = graphLinkNodeId(link.source);
                  const targetId = graphLinkNodeId(link.target);
                  return selectedNode && (sourceId === selectedNode.id || targetId === selectedNode.id)
                    ? 'rgba(17, 24, 39, 0.72)'
                    : 'rgba(71, 85, 105, 0.38)';
                }}
                linkDirectionalArrowLength={7}
                linkDirectionalArrowRelPos={1}
                linkLabel={(link) => link.type}
                warmupTicks={80}
                cooldownTicks={260}
                enableNodeDrag
                onNodeClick={setSelectedNode}
                onNodeDragEnd={(node) => {
                  node.fx = node.x;
                  node.fy = node.y;
                }}
                onBackgroundClick={() => setSelectedNode(null)}
                nodePointerAreaPaint={(node, color, ctx) => {
                  const radius = estimateGraphNodeRadius(node) + 10;
                  ctx.fillStyle = color;
                  ctx.beginPath();
                  ctx.arc(node.x, node.y, radius, 0, 2 * Math.PI, false);
                  ctx.fill();
                }}
                nodeCanvasObject={(node, ctx, globalScale) => {
                  const lines = graphNodeTextLines(node);
                  const radius = estimateGraphNodeRadius(node);
                  const fontSize = Math.max(9, Math.min(12, 12 / Math.sqrt(globalScale)));
                  const lineHeight = fontSize + 2;
                  const startY = node.y - ((lines.length - 1) * lineHeight) / 2;

                  ctx.beginPath();
                  ctx.arc(node.x, node.y, radius, 0, 2 * Math.PI, false);
                  ctx.fillStyle = graphNodeColor(node);
                  ctx.fill();
                  ctx.lineWidth = selectedNode?.id === node.id ? 4 : 2;
                  ctx.strokeStyle = selectedNode?.id === node.id ? '#111827' : '#ffffff';
                  ctx.stroke();

                  ctx.textAlign = 'center';
                  ctx.textBaseline = 'middle';
                  ctx.font = `800 ${fontSize}px Segoe UI, sans-serif`;
                  ctx.fillStyle = '#ffffff';
                  ctx.shadowColor = 'rgba(15, 23, 42, 0.5)';
                  ctx.shadowBlur = 2;
                  lines.forEach((line, index) => {
                    ctx.fillText(line, node.x, startY + index * lineHeight);
                  });
                  ctx.shadowBlur = 0;
                }}
              />
            </div>
          )}
        </section>

        <aside className="card java-graph-detail-panel">
          <div className="panel-title">
            <Search size={18} />
            <div>
              <h2>{TEXT.detailTitle}</h2>
              <p>{selectedNode ? selectedNode.label : TEXT.noSelection}</p>
            </div>
          </div>

          {selectedNode ? (
            <div className="java-graph-node-detail">
              <dl>
                <div>
                  <dt>{TEXT.label}</dt>
                  <dd>{selectedNode.label}</dd>
                </div>
                <div>
                  <dt>{TEXT.name}</dt>
                  <dd>{graphNodeName(selectedNode)}</dd>
                </div>
                <div>
                  <dt>{TEXT.id}</dt>
                  <dd>{selectedNode.id}</dd>
                </div>
              </dl>

              <div className="history-section">
                <h3>{TEXT.properties}</h3>
                <pre className="history-text-block">{JSON.stringify(selectedNode.properties, null, 2)}</pre>
              </div>
            </div>
          ) : (
            <div className="empty-result java-graph-empty-detail">
              <span>{TEXT.noSelection}</span>
            </div>
          )}
        </aside>
      </section>
    </section>
  );
}