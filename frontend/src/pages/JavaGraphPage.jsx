import { useEffect, useMemo, useRef, useState } from 'react';
import { forceCollide, forceX, forceY } from 'd3-force-3d';
import ForceGraph2D from 'react-force-graph-2d';
import { FileCode, FolderTree, Network, RotateCcw, Search, X } from 'lucide-react';
import { fetchSourceGraphNodeSource, fetchSourceGraphOverview } from '../api/sourceGraphApi.js';
import { isApiRequestError } from '../api/apiClient.js';
import { Loading } from '../components/common/Loading.jsx';
import { Modal } from '../components/common/Modal.jsx';
import { fetchKnowledgeProjects } from '../api/projectApi.js';
import { ProjectSelect } from '../components/common/ProjectSelect.jsx';

const TEXT = {
  title: 'Java Graph',
  description: '패키지를 선택하면 해당 소스와 직접 연결된 외부 패키지 관계를 표시합니다.',
  searchLabel: '\uAC80\uC0C9\uC5B4',
  searchPlaceholder: 'FQN, \uD30C\uC77C\uBA85, \uB178\uB4DC \uC720\uD615, source',
  search: '\uAC80\uC0C9',
  reset: '\uCD08\uAE30\uD654',
  graphTitle: '패키지 관계도',
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
  sourceModalTitle: 'Java ???????곸뒠',
  sourceLoading: '???????곸뒠???븍뜄???삳뮉 餓λ쵐???덈뼄.',
  sourceUnavailable: '??뽯뻻?????????곸뒠??筌≪뼚??????곷뮸??덈뼄.',
};

const NODE_COLORS = {
  Controller: '#2563eb',
  Service: '#0f766e',
  Repository: '#b45309',
  Mapper: '#7c3aed',
  DataModel: '#15803d',
  Common: '#64748b',
  ApiEndpoint: '#0284c7',
  SqlStatement: '#c2410c',
  DatabaseTable: '#475569',
};

const NODE_LEGEND = [
  { label: 'Controller', meaning: 'API 진입 계층', color: NODE_COLORS.Controller },
  { label: 'Service', meaning: 'Service / ServiceImpl', color: NODE_COLORS.Service },
  { label: 'Repository', meaning: '데이터 접근 계층', color: NODE_COLORS.Repository },
  { label: 'Mapper / XML', meaning: 'MyBatis 인터페이스와 XML', color: NODE_COLORS.Mapper },
  { label: 'DTO / Domain', meaning: '데이터 모델', color: NODE_COLORS.DataModel },
  { label: 'Common', meaning: '연결된 공통 클래스', color: NODE_COLORS.Common },
  { label: 'API', meaning: 'HTTP Endpoint', color: NODE_COLORS.ApiEndpoint },
  { label: 'SQL', meaning: 'MyBatis Statement', color: NODE_COLORS.SqlStatement },
  { label: 'Table', meaning: '조회·변경 대상 테이블', color: NODE_COLORS.DatabaseTable },
];

const LEGEND_ROLE_BY_LABEL = {
  Controller: 'Controller',
  Service: 'Service',
  Repository: 'Repository',
  'Mapper / XML': 'Mapper',
  'DTO / Domain': 'DataModel',
  Common: 'Common',
  API: 'ApiEndpoint',
  SQL: 'SqlStatement',
  Table: 'DatabaseTable',
};

const NODE_RADIUS_BY_ROLE = {
  Controller: 40,
  Service: 38,
  Repository: 38,
  Mapper: 38,
  DataModel: 36,
  Common: 34,
  ApiEndpoint: 34,
  SqlStatement: 34,
  DatabaseTable: 36,
};

const TYPE_RELATIONSHIPS = new Set(['IMPORTS', 'INJECTS', 'IMPLEMENTS', 'EXTENDS', 'USES', 'USES_DTO', 'MAPS_TO']);

function isExternalType(node) {
  return node.properties?.external === true || node.properties?.external === 'true';
}

function typeFqn(node) {
  return String(node.properties?.fqn ?? node.properties?.namespace ?? '').trim();
}

function commonPackagePrefix(packages) {
  if (packages.length === 0) return '';
  const segments = packages.map((packageName) => packageName.split('.').filter(Boolean));
  const common = [];
  const shortestLength = Math.min(...segments.map((value) => value.length));
  for (let index = 0; index < shortestLength; index += 1) {
    const candidate = segments[0][index];
    if (!segments.every((value) => value[index] === candidate)) break;
    common.push(candidate);
  }
  return common.length >= 2 ? common.join('.') : '';
}

function graphNodeRole(node) {
  const layer = node.properties?.layer;
  if (layer === 'Controller') return 'Controller';
  if (layer === 'Service' || layer === 'ServiceImpl') return 'Service';
  if (layer === 'Repository') return 'Repository';
  if (layer === 'Mapper' || (node.label === 'SourceFile' && /\.xml$/i.test(graphNodeName(node)))) return 'Mapper';
  if (layer === 'DTO' || layer === 'Domain') return 'DataModel';
  if (node.label === 'ApiEndpoint') return 'ApiEndpoint';
  if (node.label === 'SqlStatement') return 'SqlStatement';
  if (node.label === 'DatabaseTable') return 'DatabaseTable';
  return 'Common';
}

function graphNodeName(node) {
  return node.name || node.properties?.simpleName || node.properties?.fileName || node.id;
}

function canViewNodeSource(node) {
  if (node?.label === 'JavaType') return true;
  if (node?.label !== 'SourceFile') return false;

  const sourcePath = String(
    node.properties?.filePath
      ?? node.properties?.fileName
      ?? graphNodeName(node)
      ?? '',
  );
  return /\.xml$/i.test(sourcePath);
}

function graphNodeColor(node) {
  return NODE_COLORS[graphNodeRole(node)] ?? NODE_COLORS.Common;
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
  const baseRadius = NODE_RADIUS_BY_ROLE[graphNodeRole(node)] ?? 34;
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

function javaTypePackage(node) {
  const explicitPackage = String(node.properties?.packageName ?? '').trim();
  if (explicitPackage) return explicitPackage;
  const fqn = typeFqn(node);
  return fqn.includes('.') ? fqn.slice(0, fqn.lastIndexOf('.')) : '(default package)';
}

function buildPackageCatalog(graph) {
  const javaTypes = (graph.nodes ?? [])
    .filter((node) => node.label === 'JavaType' && !isExternalType(node))
    .map((node) => ({
      id: node.id,
      label: node.label,
      name: node.name,
      properties: node.properties ?? {},
      architectureRole: graphNodeRole(node),
    }));
  const packageNames = [...new Set(javaTypes.map(javaTypePackage))]
    .sort((left, right) => left.localeCompare(right));
  const root = commonPackagePrefix(packageNames.filter((name) => name !== '(default package)'));
  const entries = packageNames.map((name) => {
    const relativeName = root && name.startsWith(`${root}.`)
      ? name.slice(root.length + 1)
      : name === root ? '(root)' : name;
    return {
      name,
      relativeName,
      depth: Math.max(0, relativeName.split('.').filter(Boolean).length - 1),
      types: javaTypes
        .filter((node) => javaTypePackage(node) === name)
        .sort((left, right) => graphNodeDisplayName(left).localeCompare(graphNodeDisplayName(right))),
    };
  });
  return { root, entries };
}

function transformPackageGraph(graph, selectedPackage, showRelatedPackages = true) {
  const allNodes = (graph.nodes ?? []).map((node) => ({
    id: node.id,
    label: node.label,
    name: node.name,
    properties: node.properties ?? {},
  }));
  const nodeMap = new Map(allNodes.map((node) => [node.id, node]));
  const relationships = graph.relationships ?? [];
  const methodOwner = new Map();
  relationships.forEach((relationship) => {
    if (relationship.type === 'HAS_METHOD') methodOwner.set(relationship.targetId, relationship.sourceId);
  });

  const javaTypes = allNodes.filter((node) => node.label === 'JavaType');
  const typeIds = new Set(javaTypes.map((node) => node.id));
  const selectedTypeIds = new Set(javaTypes
    .filter((node) => !isExternalType(node) && javaTypePackage(node) === selectedPackage)
    .map((node) => node.id));
  const typeAdjacency = new Map();
  const connectTypes = (sourceId, targetId) => {
    if (!typeIds.has(sourceId) || !typeIds.has(targetId) || sourceId === targetId) return;
    if (!typeAdjacency.has(sourceId)) typeAdjacency.set(sourceId, new Set());
    if (!typeAdjacency.has(targetId)) typeAdjacency.set(targetId, new Set());
    typeAdjacency.get(sourceId).add(targetId);
    typeAdjacency.get(targetId).add(sourceId);
  };
  relationships.forEach((relationship) => {
    if (TYPE_RELATIONSHIPS.has(relationship.type)) {
      connectTypes(relationship.sourceId, relationship.targetId);
    }
    if (relationship.type === 'CALLS') {
      connectTypes(methodOwner.get(relationship.sourceId), methodOwner.get(relationship.targetId));
    }
  });

  const relatedTypeIds = new Set();
  if (showRelatedPackages) {
    selectedTypeIds.forEach((typeId) => {
      (typeAdjacency.get(typeId) ?? []).forEach((relatedId) => {
        const related = nodeMap.get(relatedId);
        if (related && !isExternalType(related) && !selectedTypeIds.has(relatedId)) {
          relatedTypeIds.add(relatedId);
        }
      });
    });
  }
  const includedTypeIds = new Set([...selectedTypeIds, ...relatedTypeIds]);
  const includedIds = new Set(includedTypeIds);
  const links = [];
  const linkKeys = new Set();
  const addLink = (source, target, type, properties = {}) => {
    if (!source || !target || source === target) return;
    const key = `${source}|${type}|${target}`;
    if (linkKeys.has(key)) return;
    linkKeys.add(key);
    links.push({ id: key, source, target, type, properties });
  };
  const touchesSelected = (sourceId, targetId) => (
    selectedTypeIds.has(sourceId) || selectedTypeIds.has(targetId)
  );

  relationships.forEach((relationship) => {
    if (includedTypeIds.has(relationship.sourceId) && includedTypeIds.has(relationship.targetId)
        && TYPE_RELATIONSHIPS.has(relationship.type)
        && touchesSelected(relationship.sourceId, relationship.targetId)) {
      addLink(relationship.sourceId, relationship.targetId, relationship.type, relationship.properties);
    }
    if (relationship.type === 'CALLS') {
      const sourceType = methodOwner.get(relationship.sourceId);
      const targetType = methodOwner.get(relationship.targetId);
      if (includedTypeIds.has(sourceType) && includedTypeIds.has(targetType)
          && touchesSelected(sourceType, targetType)) {
        addLink(sourceType, targetType, 'CALLS', relationship.properties);
      }
    }
    if (relationship.type === 'HAS_MAPPER_XML' && selectedTypeIds.has(relationship.targetId)) {
      includedIds.add(relationship.sourceId);
      addLink(relationship.sourceId, relationship.targetId, relationship.type, relationship.properties);
    }
    if (relationship.type === 'HAS_STATEMENT' && selectedTypeIds.has(relationship.sourceId)) {
      includedIds.add(relationship.targetId);
      addLink(relationship.sourceId, relationship.targetId, relationship.type, relationship.properties);
    }
    if (relationship.type === 'HANDLED_BY') {
      const ownerType = methodOwner.get(relationship.targetId);
      if (selectedTypeIds.has(ownerType)) {
        includedIds.add(relationship.sourceId);
        addLink(relationship.sourceId, ownerType, relationship.type, relationship.properties);
      }
    }
    if (relationship.type === 'READS_FROM' || relationship.type === 'WRITES_TO') {
      const ownerType = methodOwner.get(relationship.sourceId);
      if (selectedTypeIds.has(ownerType)) {
        includedIds.add(relationship.targetId);
        addLink(ownerType, relationship.targetId, relationship.type, relationship.properties);
      }
    }
  });

  relationships.forEach((relationship) => {
    const sourceNode = nodeMap.get(relationship.sourceId);
    const targetNode = nodeMap.get(relationship.targetId);
    const statementToTable = includedIds.has(relationship.sourceId)
      && sourceNode?.label === 'SqlStatement'
      && targetNode?.label === 'DatabaseTable';
    const mapperToTable = selectedTypeIds.has(relationship.sourceId)
      && targetNode?.label === 'DatabaseTable';
    if (statementToTable || mapperToTable) {
      includedIds.add(relationship.targetId);
      addLink(relationship.sourceId, relationship.targetId, relationship.type, relationship.properties);
    }
  });

  const nodes = allNodes
    .filter((node) => includedIds.has(node.id))
    .map((node) => ({
      ...node,
      architectureRole: graphNodeRole(node),
      graphScope: relatedTypeIds.has(node.id) ? 'related-package' : 'selected-package',
      packageName: node.label === 'JavaType' ? javaTypePackage(node) : selectedPackage,
    }));
  return {
    nodes,
    links,
    selectedTypeCount: selectedTypeIds.size,
    relatedTypeCount: relatedTypeIds.size,
  };
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
  const [focusedRole, setFocusedRole] = useState(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [projects, setProjects] = useState([]);
  const [projectKey, setProjectKey] = useState('');
  const [selectedPackage, setSelectedPackage] = useState('');
  const [showRelatedPackages, setShowRelatedPackages] = useState(true);
  const [sourceModal, setSourceModal] = useState({
    open: false,
    node: null,
    source: null,
    isLoading: false,
    error: '',
  });
  const sourceRequestIdRef = useRef(0);

  const packageCatalog = useMemo(() => buildPackageCatalog(graph), [graph]);
  const effectivePackage = packageCatalog.entries.some((entry) => entry.name === selectedPackage)
    ? selectedPackage
    : packageCatalog.entries[0]?.name ?? '';
  const selectedPackageEntry = packageCatalog.entries.find((entry) => entry.name === effectivePackage);
  const graphData = useMemo(
    () => transformPackageGraph(graph, effectivePackage, showRelatedPackages),
    [graph, effectivePackage, showRelatedPackages],
  );

  async function loadGraph(nextQuery = submittedQuery) {
    setIsLoading(true);
    setError('');
    closeSourceModal();

    try {
      const result = await fetchSourceGraphOverview({ query: nextQuery, limit: 1500, projectKey });
      setGraph(result);
      setSelectedNode(null);
      setFocusedRole(null);
    } catch (exception) {
      setGraph({ nodes: [], relationships: [] });
      setSelectedNode(null);
      setFocusedRole(null);
      setError(isApiRequestError(exception) ? '' : exception.message);
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

  function closeSourceModal() {
    sourceRequestIdRef.current += 1;
    setSourceModal({
      open: false,
      node: null,
      source: null,
      isLoading: false,
      error: '',
    });
  }

  async function openNodeSource(node) {
    if (!node?.id || !canViewNodeSource(node)) {
      return;
    }

    const requestId = sourceRequestIdRef.current + 1;
    sourceRequestIdRef.current = requestId;
    setSourceModal({
      open: true,
      node,
      source: null,
      isLoading: true,
      error: '',
    });

    try {
      const source = await fetchSourceGraphNodeSource(node.id);
      if (sourceRequestIdRef.current !== requestId) {
        return;
      }
      setSourceModal({
        open: true,
        node,
        source,
        isLoading: false,
        error: source.available ? '' : source.message || TEXT.sourceUnavailable,
      });
    } catch (exception) {
      if (sourceRequestIdRef.current !== requestId) {
        return;
      }
      setSourceModal(isApiRequestError(exception) ? {
        open: false,
        node: null,
        source: null,
        isLoading: false,
        error: '',
      } : {
        open: true,
        node,
        source: null,
        isLoading: false,
        error: exception.message || TEXT.sourceUnavailable,
      });
    }
  }

  function handleNodeClick(node) {
    setSelectedNode(node);
  }

  function handleLegendClick(role) {
    setSelectedNode(null);
    setFocusedRole((currentRole) => (currentRole === role ? null : role));
  }

  function handlePackageSelect(packageName) {
    setSelectedPackage(packageName);
    setSelectedNode(null);
    setFocusedRole(null);
    closeSourceModal();
  }
  useEffect(() => {
    fetchKnowledgeProjects().then((items) => {
      setProjects(items);
      setProjectKey(items[0]?.projectKey || '');
    }).catch(() => {
      setProjects([]);
      setProjectKey('');
    });
  }, []);

  useEffect(() => {
    if (projectKey) loadGraph('');
  }, [projectKey]);

  useEffect(() => {
    setSelectedPackage((current) => (
      packageCatalog.entries.some((entry) => entry.name === current)
        ? current
        : packageCatalog.entries[0]?.name ?? ''
    ));
  }, [packageCatalog]);

  useEffect(() => {
    if (!selectedNode) return undefined;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [selectedNode]);

  useEffect(() => {
    if (!selectedNode || sourceModal.open) return undefined;

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setSelectedNode(null);
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedNode, sourceModal.open]);

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
    graphInstance.d3Force('x', forceX((node) => (
      node.graphScope === 'related-package' ? 300 : -80
    )).strength(0.035));
    graphInstance.d3Force('y', forceY(0).strength(0.018));
    graphInstance.d3ReheatSimulation?.();

    const timer = window.setTimeout(() => {
      graphInstance.zoomToFit?.(700, 90);
    }, 900);

    return () => window.clearTimeout(timer);
  }, [graphData, graphSize.width, graphSize.height]);

  useEffect(() => {
    if (!forceGraphRef.current || graphData.nodes.length === 0) return undefined;

    const graphInstance = forceGraphRef.current;
    const matchingNodes = focusedRole
      ? graphData.nodes.filter((node) => graphNodeRole(node) === focusedRole)
      : graphData.nodes;
    if (matchingNodes.length === 0) return undefined;

    const timer = window.setTimeout(() => {
      graphInstance.zoomToFit?.(
        500,
        focusedRole ? 120 : 90,
        focusedRole ? (node) => graphNodeRole(node) === focusedRole : undefined,
      );
    }, 0);

    return () => window.clearTimeout(timer);
  }, [focusedRole, graphData]);

  const sourceDetail = sourceModal.source;
  const sourceTitle = sourceDetail?.name || (sourceModal.node ? graphNodeName(sourceModal.node) : TEXT.sourceModalTitle);

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
          <ProjectSelect
            projects={projects}
            value={projectKey}
            onChange={setProjectKey}
            className="java-graph-project-field"
          />
          <label className="field java-graph-search-field">
            <span>{TEXT.searchLabel}</span>
            <input
              value={query}
              placeholder={TEXT.searchPlaceholder}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>

          <div className="java-graph-search-actions">
            <button className="button primary" type="submit" disabled={isLoading || !projectKey}>
              <Search size={17} aria-hidden="true" />
              <span>{TEXT.search}</span>
            </button>
            <button className="button secondary" type="button" onClick={handleReset} disabled={isLoading || !projectKey}>
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
                <p>{effectivePackage || (submittedQuery ? submittedQuery : '패키지를 선택해 주세요.')}</p>
              </div>
            </div>
            <div className="java-graph-toolbar-side">
              <div className="java-graph-stats">
                <span>패키지 클래스: <strong>{graphData.selectedTypeCount}</strong></span>
                <span>외부 연결: <strong>{graphData.relatedTypeCount}</strong></span>
                <span>{TEXT.links}: <strong>{graphData.links.length}</strong></span>
              </div>
              <div className="java-graph-legend" aria-label="Node color legend">
                {NODE_LEGEND.map((item) => {
                  const role = LEGEND_ROLE_BY_LABEL[item.label];
                  return (
                  <button
                    className={`java-graph-legend-item${focusedRole === role ? ' is-active' : ''}`}
                    key={item.label}
                    type="button"
                    title={item.meaning}
                    aria-label={`${item.label}: ${item.meaning}`}
                    aria-pressed={focusedRole === role}
                    onClick={() => handleLegendClick(role)}
                  >
                    <i style={{ backgroundColor: item.color }} aria-hidden="true" />
                    <strong>{item.label}</strong>
                  </button>
                  );
                })}
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
            <div className="java-graph-workbench">
              <aside className="java-package-browser" aria-label="Java 패키지 탐색기">
                <div className="java-package-browser-heading">
                  <FolderTree size={17} aria-hidden="true" />
                  <div>
                    <strong>패키지 구조</strong>
                    <small>{packageCatalog.root || '프로젝트 패키지'}</small>
                  </div>
                </div>

                <div className="java-package-tree">
                  {packageCatalog.entries.map((entry) => (
                    <button
                      className={`java-package-item${entry.name === effectivePackage ? ' is-active' : ''}`}
                      key={entry.name}
                      type="button"
                      title={entry.name}
                      aria-pressed={entry.name === effectivePackage}
                      style={{ '--package-depth': Math.min(entry.depth, 4) }}
                      onClick={() => handlePackageSelect(entry.name)}
                    >
                      <FolderTree size={14} aria-hidden="true" />
                      <span>
                        <strong>{entry.relativeName}</strong>
                        <small>{entry.types.length}</small>
                      </span>
                    </button>
                  ))}
                </div>

                <label className="java-related-package-toggle">
                  <input
                    type="checkbox"
                    checked={showRelatedPackages}
                    onChange={(event) => setShowRelatedPackages(event.target.checked)}
                  />
                  <span>
                    <strong>외부 패키지 관계</strong>
                    <small>직접 연결된 내부 소스만 표시</small>
                  </span>
                </label>

                <div className="java-package-sources">
                  <div className="java-package-sources-heading">
                    <strong>패키지 소스</strong>
                    <small>{selectedPackageEntry?.types.length ?? 0}</small>
                  </div>
                  <div className="java-package-source-list">
                    {(selectedPackageEntry?.types ?? []).map((node) => (
                      <button
                        key={node.id}
                        type="button"
                        title={`${graphNodeDisplayName(node)} 원문 보기`}
                        onClick={() => openNodeSource(node)}
                      >
                        <FileCode size={13} aria-hidden="true" />
                        <span>
                          <strong>{graphNodeDisplayName(node)}</strong>
                          <small>{graphNodeRole(node)}</small>
                        </span>
                      </button>
                    ))}
                  </div>
                </div>

                <div className="java-package-scope-legend">
                  <span><i />선택 패키지</span>
                  <span><i className="is-related" />외부 패키지</span>
                </div>
              </aside>

              <div className="java-graph-canvas" ref={graphRef}>
              <ForceGraph2D
                ref={forceGraphRef}
                width={graphSize.width}
                height={graphSize.height}
                graphData={graphData}
                nodeLabel={(node) => `${node.graphScope === 'related-package' ? '외부 패키지 · ' : ''}${graphNodeRole(node)}: ${graphNodeName(node)}${node.packageName ? `\n${node.packageName}` : ''}`}
                nodeColor={graphNodeColor}
                nodeRelSize={11}
                nodeVal={(node) => Math.max(10, estimateGraphNodeRadius(node) / 3)}
                d3AlphaDecay={0.018}
                d3VelocityDecay={0.28}
                linkDistance={graphLinkDistance}
                linkWidth={(link) => {
                  const sourceId = graphLinkNodeId(link.source);
                  const targetId = graphLinkNodeId(link.target);
                  const sourceRole = typeof link.source === 'object' ? graphNodeRole(link.source) : null;
                  const targetRole = typeof link.target === 'object' ? graphNodeRole(link.target) : null;
                  if (focusedRole) return sourceRole === focusedRole || targetRole === focusedRole ? 2 : 0.7;
                  return selectedNode && (sourceId === selectedNode.id || targetId === selectedNode.id) ? 2.4 : 1.1;
                }}
                linkColor={(link) => {
                  const sourceId = graphLinkNodeId(link.source);
                  const targetId = graphLinkNodeId(link.target);
                  const sourceRole = typeof link.source === 'object' ? graphNodeRole(link.source) : null;
                  const targetRole = typeof link.target === 'object' ? graphNodeRole(link.target) : null;
                  if (focusedRole) {
                    return sourceRole === focusedRole || targetRole === focusedRole
                      ? 'rgba(17, 24, 39, 0.62)'
                      : 'rgba(148, 163, 184, 0.12)';
                  }
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
                onNodeClick={handleNodeClick}
                onNodeDragEnd={(node) => {
                  node.fx = node.x;
                  node.fy = node.y;
                }}
                onBackgroundClick={() => {
                  setSelectedNode(null);
                }}
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

                  ctx.save();
                  const isRelatedPackage = node.graphScope === 'related-package';
                  if (isRelatedPackage) ctx.globalAlpha = 0.72;
                  if (focusedRole && graphNodeRole(node) !== focusedRole) ctx.globalAlpha = 0.16;
                  ctx.beginPath();
                  ctx.arc(node.x, node.y, radius, 0, 2 * Math.PI, false);
                  ctx.fillStyle = graphNodeColor(node);
                  ctx.fill();
                  ctx.lineWidth = selectedNode?.id === node.id ? 4 : 2;
                  ctx.strokeStyle = selectedNode?.id === node.id
                    ? '#111827'
                    : isRelatedPackage ? '#334155' : '#ffffff';
                  ctx.setLineDash(isRelatedPackage ? [6, 4] : []);
                  ctx.stroke();
                  ctx.setLineDash([]);

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
                  ctx.restore();
                }}
              />
              </div>
            </div>
          )}
        </section>

        {selectedNode && (
          <aside
            className="card java-graph-detail-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="java-graph-detail-title"
          >
          <div className="panel-title">
            <Search size={18} />
            <div>
              <h2 id="java-graph-detail-title">{TEXT.detailTitle}</h2>
              <p>{selectedNode ? graphNodeRole(selectedNode) : TEXT.noSelection}</p>
            </div>
            <button
              className="icon-button java-graph-detail-close"
              type="button"
              aria-label={'\uB178\uB4DC \uC0C1\uC138 \uB2EB\uAE30'}
              onClick={() => setSelectedNode(null)}
            >
              <X size={18} aria-hidden="true" />
            </button>
          </div>

          {selectedNode ? (
            <div className="java-graph-node-detail">
              <div className="java-graph-detail-actions">
                <div>
                  <span className="eyebrow">{graphNodeRole(selectedNode)}</span>
                  <strong>{graphNodeName(selectedNode)}</strong>
                </div>
                {canViewNodeSource(selectedNode) && (
                  <button className="button secondary" type="button" onClick={() => openNodeSource(selectedNode)}>
                    <FileCode size={16} aria-hidden="true" />
                    <span>{'\uC6D0\uBB38 \uBCF4\uAE30'}</span>
                  </button>
                )}
              </div>
              <dl>
                <div>
                  <dt>{TEXT.label}</dt>
                  <dd>{graphNodeRole(selectedNode)} {'\u00B7'} {selectedNode.label}</dd>
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
        )}
      </section>

      <Modal
        backdropClassName="java-source-modal-backdrop"
        className="java-source-modal-shell"
        open={sourceModal.open}
        onClose={closeSourceModal}
        title={sourceTitle}
      >
        <div className="java-source-modal">
          <div className="java-source-modal-title">
            <FileCode size={18} aria-hidden="true" />
            <span>{sourceDetail?.fileName || sourceDetail?.fqn || sourceTitle}</span>
          </div>

          {sourceDetail && (
            <div className="java-source-meta">
              {sourceDetail.label && <span>{sourceDetail.label}</span>}
              {sourceDetail.fileName && <span>{sourceDetail.fileName}</span>}
              {sourceDetail.fqn && <span>{sourceDetail.fqn}</span>}
              {sourceDetail.sourceKind && <span>{sourceDetail.sourceKind}</span>}
              {sourceDetail.graphSourceKey && <span>{sourceDetail.graphSourceKey}</span>}
              {sourceDetail.filePath && <span>{sourceDetail.filePath}</span>}
            </div>
          )}

          {sourceModal.isLoading ? (
            <div className="java-source-loading">
              <Loading />
              <span>{TEXT.sourceLoading}</span>
            </div>
          ) : sourceModal.error && !sourceDetail?.content ? (
            <div className="empty-result java-source-empty">
              <span>{sourceModal.error || TEXT.sourceUnavailable}</span>
            </div>
          ) : (
            <pre className="java-source-code">{sourceDetail?.content || TEXT.sourceUnavailable}</pre>
          )}
        </div>
      </Modal>
    </section>
  );
}
