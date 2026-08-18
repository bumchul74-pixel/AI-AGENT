import { useEffect, useMemo, useRef, useState } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import {
  ArrowLeftRight,
  Boxes,
  ChevronDown,
  ChevronRight,
  FileCode,
  Folder,
  FolderOpen,
  FolderTree,
  Network,
  RotateCcw,
  Search,
  Table2,
  Workflow,
  X,
} from 'lucide-react';
import { fetchSourceGraphNodeSource, fetchSourceGraphOverview } from '../api/sourceGraphApi.js';
import { isApiRequestError } from '../api/apiClient.js';
import { Loading } from '../components/common/Loading.jsx';
import { Modal } from '../components/common/Modal.jsx';
import { fetchKnowledgeProjects } from '../api/projectApi.js';
import { ProjectSelect } from '../components/common/ProjectSelect.jsx';
import { PackageDependencyMatrix } from '../components/graph/PackageDependencyMatrix.jsx';
import {
  FLOW_COLUMNS,
  buildClassImpactGraph,
  buildLayerFlowGraph,
  buildPackageContainerGraph,
  buildPackageDependencyAnalysis,
} from '../utils/sourceGraphAnalysis.js';

const GRAPH_VIEWS = {
  hierarchy: { label: '패키지 계층', description: '패키지 컨테이너와 내부 클래스를 함께 표시합니다.', icon: Boxes },
  impact: { label: '클래스 영향도', description: '유입 의존성은 왼쪽, 유출 의존성은 오른쪽에 표시합니다.', icon: ArrowLeftRight },
  flow: { label: '계층 흐름', description: 'Controller에서 Service와 Data Access를 거쳐 DB로 이어지는 흐름입니다.', icon: Workflow },
  matrix: { label: 'Dependency Matrix', description: '패키지 간 방향별 의존 건수와 순환 의존성을 분석합니다.', icon: Table2 },
};

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

function fitCanvasText(ctx, value, maxWidth) {
  const text = String(value ?? '');
  if (!text || ctx.measureText(text).width <= maxWidth) return text;
  let low = 1;
  let high = text.length;
  let fitted = '...';
  while (low <= high) {
    const visibleLength = Math.floor((low + high) / 2);
    const leadingLength = Math.ceil(visibleLength / 2);
    const trailingLength = Math.floor(visibleLength / 2);
    const candidate = `${text.slice(0, leadingLength)}...${text.slice(text.length - trailingLength)}`;
    if (ctx.measureText(candidate).width <= maxWidth) {
      fitted = candidate;
      low = visibleLength + 1;
    } else {
      high = visibleLength - 1;
    }
  }
  return fitted;
}

function estimateGraphNodeRadius(node) {
  const baseRadius = NODE_RADIUS_BY_ROLE[graphNodeRole(node)] ?? 34;
  const lines = graphNodeTextLines(node);
  const longestLine = Math.max(...lines.map((line) => line.length));
  const textRadius = longestLine * 4.6 + 18;
  const lineRadius = lines.length > 1 ? 40 : 32;

  return Math.ceil(Math.max(baseRadius, textRadius, lineRadius));
}

function visualNodeRadius(node) {
  if (node.nodeKind === 'class') return 27;
  if (node.nodeKind === 'impact') return node.impactSide === 'selected' ? 46 : 36;
  if (node.nodeKind === 'flow') return 36;
  if (node.nodeKind === 'database') return 40;
  return estimateGraphNodeRadius(node);
}

function graphNodeBoundaryPoint(node, toward) {
  const x = node.x ?? node.fx ?? 0;
  const y = node.y ?? node.fy ?? 0;
  const deltaX = (toward.x ?? 0) - x;
  const deltaY = (toward.y ?? 0) - y;
  const distance = Math.hypot(deltaX, deltaY) || 1;

  if (node.nodeKind === 'package') {
    const halfWidth = node.containerWidth / 2;
    const halfHeight = node.containerHeight / 2;
    const scale = Math.min(
      deltaX === 0 ? Number.POSITIVE_INFINITY : halfWidth / Math.abs(deltaX),
      deltaY === 0 ? Number.POSITIVE_INFINITY : halfHeight / Math.abs(deltaY),
    );
    return { x: x + deltaX * scale, y: y + deltaY * scale };
  }

  const radius = visualNodeRadius(node) + 1;
  return { x: x + (deltaX / distance) * radius, y: y + (deltaY / distance) * radius };
}

function graphLinkBoundaryPoints(link) {
  const { source, target } = link;
  if (!source || !target || typeof source !== 'object' || typeof target !== 'object') return null;
  const sourceCenter = { x: source.x ?? source.fx ?? 0, y: source.y ?? source.fy ?? 0 };
  const targetCenter = { x: target.x ?? target.fx ?? 0, y: target.y ?? target.fy ?? 0 };

  if (link.linkKind === 'contains' && source.nodeKind === 'package') {
    const start = {
      x: targetCenter.x,
      y: (source.y ?? source.fy ?? 0) - source.containerHeight / 2 + 48,
    };
    return { start, end: graphNodeBoundaryPoint(target, start) };
  }

  return {
    start: graphNodeBoundaryPoint(source, targetCenter),
    end: graphNodeBoundaryPoint(target, sourceCenter),
  };
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
  const entryByName = new Map(entries.map((entry) => [entry.name, entry]));
  const tree = [];
  const nodeByPath = new Map();

  packageNames.forEach((packageName) => {
    if (packageName === '(default package)') {
      const defaultNode = {
        id: packageName,
        label: packageName,
        path: packageName,
        entry: entryByName.get(packageName),
        children: [],
      };
      tree.push(defaultNode);
      nodeByPath.set(packageName, defaultNode);
      return;
    }

    let parentChildren = tree;
    let path = '';
    packageName.split('.').filter(Boolean).forEach((segment) => {
      path = path ? `${path}.${segment}` : segment;
      let node = nodeByPath.get(path);
      if (!node) {
        node = {
          id: path,
          label: segment,
          path,
          entry: null,
          children: [],
        };
        nodeByPath.set(path, node);
        parentChildren.push(node);
      }
      parentChildren = node.children;
    });

    const packageNode = nodeByPath.get(packageName);
    if (packageNode) packageNode.entry = entryByName.get(packageName);
  });

  function countTreeTypes(node) {
    const childTypeCount = node.children.reduce((sum, child) => sum + countTreeTypes(child), 0);
    node.typeCount = (node.entry?.types.length ?? 0) + childTypeCount;
    return node.typeCount;
  }
  tree.forEach(countTreeTypes);

  return {
    root,
    entries,
    tree,
    expandableIds: [...nodeByPath.values()]
      .filter((node) => node.children.length > 0)
      .map((node) => node.id),
  };
}

function PackageTreeNode({ node, depth, effectivePackage, expandedIds, onSelect, onToggle }) {
  const hasChildren = node.children.length > 0;
  const isExpanded = hasChildren && expandedIds.has(node.id);
  const isActive = node.entry?.name === effectivePackage;

  return (
    <div
      className="java-package-tree-node"
      role="treeitem"
      aria-expanded={hasChildren ? isExpanded : undefined}
      aria-level={depth + 1}
    >
      <div className="java-package-tree-row" style={{ '--package-depth': depth }}>
        {hasChildren ? (
          <button
            className="java-package-node-toggle"
            type="button"
            title={`${node.path} ${isExpanded ? '접기' : '펼치기'}`}
            aria-label={`${node.path} ${isExpanded ? '접기' : '펼치기'}`}
            onClick={() => onToggle(node.id)}
          >
            {isExpanded
              ? <ChevronDown size={13} aria-hidden="true" />
              : <ChevronRight size={13} aria-hidden="true" />}
          </button>
        ) : (
          <span className="java-package-node-spacer" aria-hidden="true" />
        )}
        <button
          className={`java-package-item${isActive ? ' is-active' : ''}${node.entry ? '' : ' is-branch'}`}
          type="button"
          title={node.entry ? node.entry.name : node.path}
          aria-current={isActive ? 'true' : undefined}
          onClick={() => (node.entry ? onSelect(node.entry.name) : onToggle(node.id))}
        >
          {isExpanded
            ? <FolderOpen size={14} aria-hidden="true" />
            : <Folder size={14} aria-hidden="true" />}
          <span>
            <strong>{node.label}</strong>
            <small title={node.entry ? '이 패키지의 클래스 수' : '하위 패키지의 클래스 수'}>
              {node.entry?.types.length ?? node.typeCount}
            </small>
          </span>
        </button>
      </div>
      {isExpanded && (
        <div role="group">
          {node.children.map((child) => (
            <PackageTreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              effectivePackage={effectivePackage}
              expandedIds={expandedIds}
              onSelect={onSelect}
              onToggle={onToggle}
            />
          ))}
        </div>
      )}
    </div>
  );
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
  const [impactNodeId, setImpactNodeId] = useState('');
  const [viewMode, setViewMode] = useState('hierarchy');
  const [hierarchyLayoutRevision, setHierarchyLayoutRevision] = useState(0);
  const [focusedRole, setFocusedRole] = useState(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [projects, setProjects] = useState([]);
  const [projectKey, setProjectKey] = useState('');
  const [selectedPackage, setSelectedPackage] = useState('');
  const [expandedPackageNodes, setExpandedPackageNodes] = useState(() => new Set());
  const [showClassDependencies, setShowClassDependencies] = useState(true);
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
  const hierarchyLayout = useMemo(
    () => buildPackageContainerGraph(graph),
    [graph, hierarchyLayoutRevision],
  );
  const hierarchyGraph = useMemo(() => (
    showClassDependencies ? hierarchyLayout : {
      ...hierarchyLayout,
      links: hierarchyLayout.links.filter((link) => link.linkKind !== 'dependency'),
    }
  ), [hierarchyLayout, showClassDependencies]);
  const impactGraph = useMemo(
    () => buildClassImpactGraph(graph, impactNodeId),
    [graph, impactNodeId],
  );
  const flowGraph = useMemo(() => buildLayerFlowGraph(graph), [graph]);
  const dependencyAnalysis = useMemo(() => buildPackageDependencyAnalysis(graph), [graph]);
  const graphData = viewMode === 'impact'
    ? impactGraph
    : viewMode === 'flow' ? flowGraph : hierarchyGraph;
  const viewMeta = GRAPH_VIEWS[viewMode];
  const viewStats = viewMode === 'hierarchy'
    ? [
      ['패키지', hierarchyGraph.packageCount],
      ['클래스', hierarchyGraph.classCount],
      ['의존 관계', hierarchyGraph.dependencyCount],
    ]
    : viewMode === 'impact'
      ? [
        ['유입', impactGraph.incomingCount],
        ['유출', impactGraph.outgoingCount],
        ['관계', impactGraph.links.length],
      ]
      : viewMode === 'flow'
        ? [
          ['Controller', flowGraph.controllerCount],
          ['Service', flowGraph.serviceCount],
          ['DB', flowGraph.databaseCount],
        ]
        : [
          ['패키지', dependencyAnalysis.packages.length],
          ['의존 관계', dependencyAnalysis.dependencyCount],
          ['순환 그룹', dependencyAnalysis.cycles.length],
        ];

  useEffect(() => {
    setExpandedPackageNodes(new Set(packageCatalog.expandableIds));
  }, [packageCatalog]);

  function handlePackageNodeToggle(nodeId) {
    setExpandedPackageNodes((current) => {
      const next = new Set(current);
      if (next.has(nodeId)) next.delete(nodeId);
      else next.add(nodeId);
      return next;
    });
  }

  async function loadGraph(nextQuery = submittedQuery) {
    setIsLoading(true);
    setError('');
    closeSourceModal();

    try {
      const result = await fetchSourceGraphOverview({ query: nextQuery, limit: 1500, projectKey });
      setGraph(result);
      setSelectedNode(null);
      setImpactNodeId('');
      setViewMode('hierarchy');
      setFocusedRole(null);
    } catch (exception) {
      setGraph({ nodes: [], relationships: [] });
      setSelectedNode(null);
      setImpactNodeId('');
      setViewMode('hierarchy');
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
    if (node.nodeKind === 'package') {
      handlePackageSelect(node.packageName);
      return;
    }
    const rawNode = node.rawNode ?? node;
    if (rawNode.label === 'JavaType') {
      setImpactNodeId(rawNode.id);
      setSelectedNode(null);
      setFocusedRole(null);
      setViewMode('impact');
      return;
    }
    setSelectedNode(rawNode);
  }

  function handleViewModeChange(nextMode) {
    if (nextMode === 'impact' && !impactNodeId) return;
    setViewMode(nextMode);
    setSelectedNode(null);
    setFocusedRole(null);
    closeSourceModal();
  }

  function fixGraphNodePosition(node, x, y) {
    node.x = x;
    node.y = y;
    node.fx = x;
    node.fy = y;
  }

  function handleHierarchyNodeDrag(node, translate = {}) {
    if (viewMode !== 'hierarchy') return;

    if (node.nodeKind === 'package') {
      const deltaX = Number.isFinite(translate.x) ? translate.x : 0;
      const deltaY = Number.isFinite(translate.y) ? translate.y : 0;
      fixGraphNodePosition(node, node.x ?? node.fx ?? 0, node.y ?? node.fy ?? 0);
      hierarchyLayout.nodes
        .filter((candidate) => (
          candidate.nodeKind === 'class' && candidate.packageName === node.packageName
        ))
        .forEach((child) => {
          fixGraphNodePosition(
            child,
            (child.fx ?? child.x ?? 0) + deltaX,
            (child.fy ?? child.y ?? 0) + deltaY,
          );
        });
    } else if (node.nodeKind === 'class') {
      const container = hierarchyLayout.nodes.find((candidate) => (
        candidate.nodeKind === 'package' && candidate.packageName === node.packageName
      ));
      if (!container) return;
      const containerX = container.fx ?? container.x ?? 0;
      const containerY = container.fy ?? container.y ?? 0;
      const radius = visualNodeRadius(node);
      const minX = containerX - container.containerWidth / 2 + radius + 10;
      const maxX = containerX + container.containerWidth / 2 - radius - 10;
      const minY = containerY - container.containerHeight / 2 + radius + 48;
      const maxY = containerY + container.containerHeight / 2 - radius - 10;
      fixGraphNodePosition(
        node,
        Math.min(maxX, Math.max(minX, node.x ?? node.fx ?? 0)),
        Math.min(maxY, Math.max(minY, node.y ?? node.fy ?? 0)),
      );
    }

    forceGraphRef.current?.refresh?.();
  }

  function handleHierarchyNodeDragEnd(node) {
    if (viewMode !== 'hierarchy') return;
    fixGraphNodePosition(node, node.fx ?? node.x ?? 0, node.fy ?? node.y ?? 0);
    forceGraphRef.current?.refresh?.();
  }

  function handleHierarchyLayoutReset() {
    setHierarchyLayoutRevision((current) => current + 1);
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
    if (!forceGraphRef.current || graphData.nodes.length === 0 || viewMode === 'matrix') {
      return undefined;
    }

    const graphInstance = forceGraphRef.current;
    const linkForce = graphInstance.d3Force('link');
    const chargeForce = graphInstance.d3Force('charge');
    linkForce?.distance((link) => {
      if (link.linkKind === 'contains') return 30;
      if (link.linkKind === 'package-hierarchy') return 220;
      if (link.linkKind === 'impact') return 320;
      return 180;
    }).strength(0);
    chargeForce?.strength(0);
    graphInstance.d3Force('collide', null);
    graphInstance.d3Force('x', null);
    graphInstance.d3Force('y', null);
    graphInstance.d3ReheatSimulation?.();

    const timer = window.setTimeout(() => {
      graphInstance.zoomToFit?.(500, viewMode === 'hierarchy' ? 48 : 100);
    }, 120);

    return () => window.clearTimeout(timer);
  }, [graphData, graphSize.width, graphSize.height, viewMode]);

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
  const ActiveViewIcon = viewMeta.icon;

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
              <ActiveViewIcon size={18} />
              <div>
                <h2>{viewMeta.label}</h2>
                <p>{viewMeta.description}</p>
              </div>
            </div>
            <div className="java-graph-toolbar-side">
              <div className="java-graph-stats">
                {viewStats.map(([label, value]) => (
                  <span key={label}>{label}: <strong>{value}</strong></span>
                ))}
              </div>
              {viewMode !== 'matrix' && (
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
              )}
            </div>
          </div>

          <div className="java-graph-view-tabs" role="tablist" aria-label="Java Graph 보기">
            {Object.entries(GRAPH_VIEWS).map(([mode, meta]) => {
              const ViewIcon = meta.icon;
              return (
                <button
                  key={mode}
                  className={viewMode === mode ? 'is-active' : ''}
                  type="button"
                  role="tab"
                  aria-selected={viewMode === mode}
                  disabled={mode === 'impact' && !impactNodeId}
                  onClick={() => handleViewModeChange(mode)}
                >
                  <ViewIcon size={15} aria-hidden="true" />
                  <span>{meta.label}</span>
                  {mode === 'matrix' && dependencyAnalysis.cycles.length > 0 && (
                    <small>{dependencyAnalysis.cycles.length}</small>
                  )}
                </button>
              );
            })}
          </div>
          {viewMode === 'hierarchy' && (
            <div className="java-graph-layout-actions">
              <span>패키지를 끌면 내부 클래스가 함께 이동합니다.</span>
              <button type="button" onClick={handleHierarchyLayoutReset}>
                <RotateCcw size={13} aria-hidden="true" />
                레이아웃 초기화
              </button>
            </div>
          )}

          {error && <p className="error-text">{error}</p>}

          {isLoading ? (
            <Loading />
          ) : hierarchyGraph.nodes.length === 0 ? (
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

                <div className="java-package-tree" role="tree" aria-label="프로젝트 패키지 계층">
                  {packageCatalog.tree.map((node) => (
                    <PackageTreeNode
                      key={node.id}
                      node={node}
                      depth={0}
                      effectivePackage={effectivePackage}
                      expandedIds={expandedPackageNodes}
                      onSelect={handlePackageSelect}
                      onToggle={handlePackageNodeToggle}
                    />
                  ))}
                </div>

                <label className="java-related-package-toggle">
                  <input
                    type="checkbox"
                    checked={showClassDependencies}
                    onChange={(event) => setShowClassDependencies(event.target.checked)}
                  />
                  <span>
                    <strong>클래스 의존 관계</strong>
                    <small>패키지 간 클래스 연결선 표시</small>
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
                        title={`${graphNodeDisplayName(node)} 영향도 보기`}
                        onClick={() => handleNodeClick(node)}
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
                  <span><i className="is-related" />클래스 의존</span>
                </div>
              </aside>

              <div className={`java-graph-main-view is-${viewMode}`}>
                {viewMode === 'matrix' ? (
                  <PackageDependencyMatrix
                    analysis={dependencyAnalysis}
                    selectedPackage={effectivePackage}
                    onSelectPackage={handlePackageSelect}
                  />
                ) : graphData.nodes.length === 0 ? (
                  <div className="empty-result java-graph-mode-empty">
                    <strong>{viewMode === 'impact'
                      ? '선택한 클래스의 직접 영향 관계가 없습니다.'
                      : 'Controller → Service → DB 흐름을 구성할 관계가 없습니다.'}</strong>
                    <span>Source Graph에 해당 방향 관계가 적재되었는지 확인해 주세요.</span>
                  </div>
                ) : (
                  <div className="java-graph-canvas" ref={graphRef}>
                    {viewMode === 'impact' && impactGraph.selected && (
                      <>
                        <div className="java-impact-selected">
                          <span>선택 클래스</span>
                          <strong>{graphNodeDisplayName(impactGraph.selected)}</strong>
                          <small>{graphNodeRole(impactGraph.selected)} · {javaTypePackage(impactGraph.selected)}</small>
                          <button type="button" onClick={() => openNodeSource(impactGraph.selected)}>
                            <FileCode size={13} aria-hidden="true" />
                            원문 보기
                          </button>
                        </div>
                        <div className="java-impact-axis" aria-hidden="true">
                          <span>유입 의존성</span>
                          <span>선택 클래스</span>
                          <span>유출 의존성</span>
                        </div>
                      </>
                    )}
                    {viewMode === 'flow' && (
                      <div className="java-flow-axis" aria-hidden="true">
                        {FLOW_COLUMNS.map((column) => <span key={column.key}>{column.label}</span>)}
                      </div>
                    )}
                    <ForceGraph2D
                      ref={forceGraphRef}
                      width={graphSize.width}
                      height={graphSize.height}
                      graphData={graphData}
                      nodeLabel={(node) => node.nodeKind === 'package'
                        ? `${node.packageName} · ${node.classCount} classes`
                        : `${graphNodeRole(node)}: ${graphNodeName(node)}${node.packageName ? `\n${node.packageName}` : ''}`}
                      nodeColor={graphNodeColor}
                      nodeRelSize={8}
                      nodeVal={(node) => node.nodeKind === 'package' ? 1 : visualNodeRadius(node) / 3}
                      d3AlphaDecay={1}
                      d3VelocityDecay={1}
                      linkCanvasObjectMode={() => 'replace'}
                      linkCanvasObject={(link, ctx, globalScale) => {
                        const points = graphLinkBoundaryPoints(link);
                        if (!points) return;
                        const { start, end } = points;
                        const deltaX = end.x - start.x;
                        const deltaY = end.y - start.y;
                        const distance = Math.hypot(deltaX, deltaY);
                        if (distance < 1) return;
                        const unitX = deltaX / distance;
                        const unitY = deltaY / distance;
                        const hasArrow = link.linkKind !== 'contains' && link.linkKind !== 'package-hierarchy';
                        const arrowLength = hasArrow ? 8 : 0;
                        const lineEndX = end.x - unitX * arrowLength;
                        const lineEndY = end.y - unitY * arrowLength;
                        const sourceRole = graphNodeRole(link.source);
                        const targetRole = graphNodeRole(link.target);
                        const isFocused = !focusedRole || sourceRole === focusedRole || targetRole === focusedRole;
                        const color = link.linkKind === 'contains'
                          ? 'rgba(148, 163, 184, 0.22)'
                          : link.linkKind === 'package-hierarchy'
                            ? 'rgba(37, 99, 235, 0.38)'
                            : link.linkKind === 'impact'
                              ? 'rgba(15, 118, 110, 0.72)'
                              : link.linkKind === 'flow'
                                ? 'rgba(37, 99, 235, 0.68)'
                                : isFocused ? 'rgba(71, 85, 105, 0.48)' : 'rgba(148, 163, 184, 0.08)';
                        const width = link.linkKind === 'contains'
                          ? 0.45
                          : link.linkKind === 'package-hierarchy'
                            ? 1.8
                            : link.linkKind === 'impact' || link.linkKind === 'flow'
                              ? 2.1
                              : focusedRole ? (isFocused ? 2 : 0.35) : 0.9;

                        ctx.save();
                        ctx.beginPath();
                        ctx.moveTo(start.x, start.y);
                        ctx.lineTo(lineEndX, lineEndY);
                        ctx.lineWidth = Math.max(width, 0.75 / globalScale);
                        ctx.strokeStyle = color;
                        ctx.stroke();
                        if (hasArrow) {
                          const arrowWidth = 4.5;
                          const baseX = end.x - unitX * arrowLength;
                          const baseY = end.y - unitY * arrowLength;
                          ctx.beginPath();
                          ctx.moveTo(end.x, end.y);
                          ctx.lineTo(baseX - unitY * arrowWidth, baseY + unitX * arrowWidth);
                          ctx.lineTo(baseX + unitY * arrowWidth, baseY - unitX * arrowWidth);
                          ctx.closePath();
                          ctx.fillStyle = color;
                          ctx.fill();
                        }
                        ctx.restore();
                      }}
                      linkWidth={(link) => {
                        if (link.linkKind === 'contains') return 0.45;
                        if (link.linkKind === 'package-hierarchy') return 1.8;
                        if (link.linkKind === 'impact' || link.linkKind === 'flow') return 2.1;
                        const sourceRole = typeof link.source === 'object' ? graphNodeRole(link.source) : null;
                        const targetRole = typeof link.target === 'object' ? graphNodeRole(link.target) : null;
                        if (focusedRole) return sourceRole === focusedRole || targetRole === focusedRole ? 2 : 0.35;
                        return 0.9;
                      }}
                      linkColor={(link) => {
                        if (link.linkKind === 'contains') return 'rgba(148, 163, 184, 0.22)';
                        if (link.linkKind === 'package-hierarchy') return 'rgba(37, 99, 235, 0.38)';
                        if (link.linkKind === 'impact') return 'rgba(15, 118, 110, 0.72)';
                        if (link.linkKind === 'flow') return 'rgba(37, 99, 235, 0.68)';
                        if (focusedRole) {
                          const sourceRole = typeof link.source === 'object' ? graphNodeRole(link.source) : null;
                          const targetRole = typeof link.target === 'object' ? graphNodeRole(link.target) : null;
                          return sourceRole === focusedRole || targetRole === focusedRole
                            ? 'rgba(17, 24, 39, 0.58)'
                            : 'rgba(148, 163, 184, 0.08)';
                        }
                        return 'rgba(71, 85, 105, 0.28)';
                      }}
                      linkDirectionalArrowLength={0}
                      linkLabel={(link) => link.type}
                      warmupTicks={1}
                      cooldownTicks={1}
                      enableNodeDrag={viewMode === 'hierarchy'}
                      onNodeDrag={handleHierarchyNodeDrag}
                      onNodeDragEnd={handleHierarchyNodeDragEnd}
                      onNodeClick={handleNodeClick}
                      onBackgroundClick={() => setSelectedNode(null)}
                      nodePointerAreaPaint={(node, color, ctx) => {
                        const x = node.x ?? node.fx ?? 0;
                        const y = node.y ?? node.fy ?? 0;
                        ctx.fillStyle = color;
                        ctx.beginPath();
                        if (node.nodeKind === 'package') {
                          ctx.rect(
                            x - node.containerWidth / 2,
                            y - node.containerHeight / 2,
                            node.containerWidth,
                            node.containerHeight,
                          );
                        } else if (node.nodeKind === 'class') {
                          const radius = visualNodeRadius(node);
                          ctx.rect(x - 62, y - radius - 8, 124, radius * 2 + 48);
                        } else {
                          ctx.arc(x, y, visualNodeRadius(node) + 8, 0, 2 * Math.PI, false);
                        }
                        ctx.fill();
                      }}
                      nodeCanvasObject={(node, ctx, globalScale) => {
                        const x = node.x ?? node.fx ?? 0;
                        const y = node.y ?? node.fy ?? 0;
                        ctx.save();
                        if (node.nodeKind === 'package') {
                          const left = x - node.containerWidth / 2;
                          const top = y - node.containerHeight / 2;
                          const isSelectedPackage = node.packageName === effectivePackage;
                          ctx.beginPath();
                          ctx.roundRect(left, top, node.containerWidth, node.containerHeight, 18);
                          ctx.fillStyle = isSelectedPackage
                            ? 'rgba(37, 99, 235, 0.13)'
                            : 'rgba(226, 232, 240, 0.72)';
                          ctx.fill();
                          ctx.lineWidth = isSelectedPackage ? 4 : 2;
                          ctx.strokeStyle = isSelectedPackage ? '#2563eb' : '#94a3b8';
                          ctx.stroke();
                          ctx.beginPath();
                          ctx.roundRect(left + 3, top + 3, node.containerWidth - 6, 44, 14);
                          ctx.fillStyle = isSelectedPackage ? 'rgba(239, 246, 255, 0.98)' : 'rgba(255, 255, 255, 0.94)';
                          ctx.fill();
                          ctx.beginPath();
                          ctx.moveTo(left + 12, top + 48);
                          ctx.lineTo(left + node.containerWidth - 12, top + 48);
                          ctx.lineWidth = 1;
                          ctx.strokeStyle = isSelectedPackage ? '#93c5fd' : '#cbd5e1';
                          ctx.stroke();
                          const countText = `${node.classCount} classes`;
                          ctx.font = '800 11px Segoe UI, sans-serif';
                          const countWidth = Math.ceil(ctx.measureText(countText).width) + 18;
                          const countLeft = left + node.containerWidth - countWidth - 14;
                          ctx.beginPath();
                          ctx.roundRect(countLeft, top + 13, countWidth, 22, 11);
                          ctx.fillStyle = isSelectedPackage ? '#dbeafe' : '#e2e8f0';
                          ctx.fill();
                          ctx.fillStyle = isSelectedPackage ? '#1d4ed8' : '#475569';
                          ctx.textAlign = 'center';
                          ctx.textBaseline = 'middle';
                          ctx.fillText(countText, countLeft + countWidth / 2, top + 24);
                          ctx.fillStyle = isSelectedPackage ? '#1d4ed8' : '#334155';
                          ctx.font = '800 17px Segoe UI, sans-serif';
                          ctx.textAlign = 'left';
                          const packageLabel = fitCanvasText(ctx, node.packageName, Math.max(60, countLeft - left - 30));
                          ctx.fillText(packageLabel, left + 16, top + 24);
                          ctx.restore();
                          return;
                        }

                        const lines = graphNodeTextLines(node);
                        const radius = visualNodeRadius(node);
                        const fontSize = Math.max(9, Math.min(12, 12 / Math.sqrt(globalScale)));
                        const lineHeight = fontSize + 2;
                        const startY = y - ((lines.length - 1) * lineHeight) / 2;
                        if (focusedRole && graphNodeRole(node) !== focusedRole) ctx.globalAlpha = 0.14;
                        ctx.beginPath();
                        ctx.arc(x, y, radius, 0, 2 * Math.PI, false);
                        ctx.fillStyle = graphNodeColor(node);
                        ctx.fill();
                        ctx.lineWidth = node.impactSide === 'selected' ? 5 : 2;
                        ctx.strokeStyle = node.impactSide === 'selected' ? '#111827' : '#ffffff';
                        ctx.stroke();
                        if (node.nodeKind === 'class') {
                          const labelTop = y + radius + 7;
                          ctx.shadowColor = 'rgba(15, 23, 42, 0.18)';
                          ctx.shadowBlur = 5;
                          ctx.font = '800 13px Segoe UI, sans-serif';
                          const classLabel = fitCanvasText(ctx, graphNodeDisplayName(node), 104);
                          const labelWidth = Math.max(66, Math.ceil(ctx.measureText(classLabel).width) + 18);
                          ctx.beginPath();
                          ctx.roundRect(x - labelWidth / 2, labelTop, labelWidth, 25, 8);
                          ctx.fillStyle = 'rgba(255, 255, 255, 0.98)';
                          ctx.fill();
                          ctx.shadowBlur = 0;
                          ctx.lineWidth = 1.5;
                          ctx.strokeStyle = '#94a3b8';
                          ctx.stroke();
                          ctx.fillStyle = '#0f172a';
                          ctx.textAlign = 'center';
                          ctx.textBaseline = 'middle';
                          ctx.fillText(classLabel, x, labelTop + 12.5);
                          ctx.restore();
                          return;
                        }
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.font = `800 ${fontSize}px Segoe UI, sans-serif`;
                        ctx.fillStyle = '#ffffff';
                        ctx.shadowColor = 'rgba(15, 23, 42, 0.5)';
                        ctx.shadowBlur = 2;
                        lines.forEach((line, index) => {
                          ctx.fillText(line, x, startY + index * lineHeight);
                        });
                        ctx.restore();
                      }}
                    />
                  </div>
                )}
              </div>
            </div>
          )}
        </section>

        {selectedNode && (
          <aside
            className="card java-graph-detail-panel"
            role="region"
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
