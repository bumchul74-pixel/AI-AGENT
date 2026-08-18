const TYPE_RELATIONSHIPS = new Set([
  'IMPORTS',
  'INJECTS',
  'IMPLEMENTS',
  'EXTENDS',
  'USES',
  'USES_DTO',
  'MAPS_TO',
]);

function isExternalType(node) {
  return node?.properties?.external === true || node?.properties?.external === 'true';
}

function typeFqn(node) {
  return String(node?.properties?.fqn ?? node?.properties?.namespace ?? '').trim();
}

export function sourceNodePackage(node) {
  const explicitPackage = String(node?.properties?.packageName ?? '').trim();
  if (explicitPackage) return explicitPackage;
  const fqn = typeFqn(node);
  return fqn.includes('.') ? fqn.slice(0, fqn.lastIndexOf('.')) : '(default package)';
}

export function sourceNodeRole(node) {
  const layer = node?.properties?.layer;
  if (layer === 'Controller') return 'Controller';
  if (layer === 'Service' || layer === 'ServiceImpl') return 'Service';
  if (layer === 'Repository') return 'Repository';
  if (layer === 'Mapper') return 'Mapper';
  if (layer === 'DTO' || layer === 'Domain') return 'DataModel';
  if (node?.label === 'DatabaseTable') return 'DatabaseTable';
  if (node?.label === 'ApiEndpoint') return 'ApiEndpoint';
  if (node?.label === 'SqlStatement') return 'SqlStatement';
  return 'Common';
}

export function sourceNodeDisplayName(node) {
  const name = node?.name || node?.properties?.simpleName || node?.properties?.fileName || node?.id || '';
  if (node?.properties?.simpleName) return node.properties.simpleName;
  if (node?.properties?.fileName) return node.properties.fileName.replace(/\.java$/i, '');
  return String(name).split('.').pop() || String(name);
}

function normalizedGraph(graph) {
  const nodes = (graph.nodes ?? []).map((node) => ({
    ...node,
    properties: node.properties ?? {},
  }));
  const nodeMap = new Map(nodes.map((node) => [node.id, node]));
  const relationships = graph.relationships ?? [];
  const methodOwner = new Map();
  const statementOwner = new Map();

  relationships.forEach((relationship) => {
    if (relationship.type === 'HAS_METHOD') methodOwner.set(relationship.targetId, relationship.sourceId);
    if (relationship.type === 'HAS_STATEMENT') statementOwner.set(relationship.targetId, relationship.sourceId);
  });

  const edgeMap = new Map();
  const addTypeEdge = (sourceId, targetId, type) => {
    const source = nodeMap.get(sourceId);
    const target = nodeMap.get(targetId);
    if (!source || !target || source.label !== 'JavaType' || target.label !== 'JavaType') return;
    if (sourceId === targetId || isExternalType(source) || isExternalType(target)) return;
    const key = `${sourceId}|${targetId}`;
    if (!edgeMap.has(key)) {
      edgeMap.set(key, { sourceId, targetId, types: new Set() });
    }
    edgeMap.get(key).types.add(type);
  };

  relationships.forEach((relationship) => {
    if (TYPE_RELATIONSHIPS.has(relationship.type)) {
      addTypeEdge(relationship.sourceId, relationship.targetId, relationship.type);
    }
    if (relationship.type === 'CALLS') {
      addTypeEdge(
        methodOwner.get(relationship.sourceId),
        methodOwner.get(relationship.targetId),
        'CALLS',
      );
    }
  });

  const databaseEdgeMap = new Map();
  const addDatabaseEdge = (sourceId, targetId, type) => {
    const source = nodeMap.get(sourceId);
    const target = nodeMap.get(targetId);
    if (!source || !target || source.label !== 'JavaType' || target.label !== 'DatabaseTable') return;
    if (isExternalType(source)) return;
    const key = `${sourceId}|${targetId}`;
    if (!databaseEdgeMap.has(key)) {
      databaseEdgeMap.set(key, { sourceId, targetId, types: new Set() });
    }
    databaseEdgeMap.get(key).types.add(type);
  };

  relationships.forEach((relationship) => {
    if (relationship.type !== 'READS_FROM'
        && relationship.type !== 'WRITES_TO'
        && relationship.type !== 'MAPS_TO') return;
    const sourceNode = nodeMap.get(relationship.sourceId);
    const sourceId = sourceNode?.label === 'JavaType'
      ? relationship.sourceId
      : methodOwner.get(relationship.sourceId) ?? statementOwner.get(relationship.sourceId);
    addDatabaseEdge(sourceId, relationship.targetId, relationship.type);
  });

  return {
    nodes,
    nodeMap,
    typeNodes: nodes.filter((node) => node.label === 'JavaType' && !isExternalType(node)),
    classEdges: [...edgeMap.values()].map((edge) => ({ ...edge, types: [...edge.types].sort() })),
    databaseEdges: [...databaseEdgeMap.values()].map((edge) => ({ ...edge, types: [...edge.types].sort() })),
  };
}

function layoutRows(items, columns, width, gap) {
  const rowHeights = [];
  items.forEach((item, index) => {
    const row = Math.floor(index / columns);
    rowHeights[row] = Math.max(rowHeights[row] ?? 0, item.height);
  });
  const rowCenters = [];
  let cursor = 0;
  rowHeights.forEach((height, index) => {
    rowCenters[index] = cursor + height / 2;
    cursor += height + gap;
  });
  return items.map((item, index) => {
    const column = index % columns;
    const row = Math.floor(index / columns);
    return {
      ...item,
      x: column * (width + gap) + width / 2,
      y: rowCenters[row],
    };
  });
}

export function buildPackageContainerGraph(graph, selectedPackage = '') {
  const normalized = normalizedGraph(graph);
  const grouped = new Map();
  normalized.typeNodes.forEach((node) => {
    const packageName = sourceNodePackage(node);
    if (!grouped.has(packageName)) grouped.set(packageName, []);
    grouped.get(packageName).push(node);
  });

  const containerWidth = 390;
  const packages = [...grouped.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([packageName, types]) => {
      const sortedTypes = [...types].sort((left, right) => (
        sourceNodeDisplayName(left).localeCompare(sourceNodeDisplayName(right))
      ));
      return {
        packageName,
        types: sortedTypes,
        height: 94 + Math.ceil(sortedTypes.length / 3) * 104,
      };
    });
  const columnCount = Math.max(1, Math.min(3, Math.ceil(Math.sqrt(packages.length))));
  const layouts = layoutRows(packages, columnCount, containerWidth, 100);
  const layoutMap = new Map(layouts.map((layout) => [layout.packageName, layout]));
  const packageNodes = [];
  const classNodes = [];
  const links = [];

  layouts.forEach((layout) => {
    const packageId = `package:${layout.packageName}`;
    packageNodes.push({
      id: packageId,
      name: layout.packageName,
      label: 'Package',
      nodeKind: 'package',
      packageName: layout.packageName,
      containerWidth,
      containerHeight: layout.height,
      classCount: layout.types.length,
      isSelectedPackage: layout.packageName === selectedPackage,
      fx: layout.x,
      fy: layout.y,
    });
    layout.types.forEach((rawNode, index) => {
      const classColumn = index % 3;
      const classRow = Math.floor(index / 3);
      classNodes.push({
        ...rawNode,
        rawNode,
        nodeKind: 'class',
        architectureRole: sourceNodeRole(rawNode),
        packageName: layout.packageName,
        fx: layout.x - 120 + classColumn * 120,
        fy: layout.y - layout.height / 2 + 83 + classRow * 104,
      });
      links.push({
        id: `${packageId}|CONTAINS|${rawNode.id}`,
        source: packageId,
        target: rawNode.id,
        type: 'CONTAINS',
        linkKind: 'contains',
      });
    });
  });

  layouts.forEach((layout) => {
    if (layout.packageName === '(default package)') return;
    const segments = layout.packageName.split('.');
    let parentName = '';
    for (let index = segments.length - 1; index > 0; index -= 1) {
      const candidate = segments.slice(0, index).join('.');
      if (layoutMap.has(candidate)) {
        parentName = candidate;
        break;
      }
    }
    if (parentName) {
      links.push({
        id: `package:${parentName}|CONTAINS|package:${layout.packageName}`,
        source: `package:${parentName}`,
        target: `package:${layout.packageName}`,
        type: 'PACKAGE',
        linkKind: 'package-hierarchy',
      });
    }
  });

  const typeIds = new Set(classNodes.map((node) => node.id));
  normalized.classEdges.forEach((edge) => {
    if (!typeIds.has(edge.sourceId) || !typeIds.has(edge.targetId)) return;
    links.push({
      id: `${edge.sourceId}|${edge.types.join('+')}|${edge.targetId}`,
      source: edge.sourceId,
      target: edge.targetId,
      type: edge.types.join(', '),
      linkKind: 'dependency',
    });
  });

  return {
    nodes: [...packageNodes, ...classNodes],
    links,
    packageCount: packageNodes.length,
    classCount: classNodes.length,
    dependencyCount: normalized.classEdges.length,
  };
}

function impactNode(rawNode, id, side, index, count) {
  return {
    ...rawNode,
    id,
    rawId: rawNode.id,
    rawNode,
    nodeKind: 'impact',
    impactSide: side,
    architectureRole: sourceNodeRole(rawNode),
    packageName: sourceNodePackage(rawNode),
    fx: side === 'incoming' ? -360 : side === 'outgoing' ? 360 : 0,
    fy: side === 'selected' ? 0 : (index - (count - 1) / 2) * 130,
  };
}

export function buildClassImpactGraph(graph, selectedTypeId) {
  const normalized = normalizedGraph(graph);
  const selected = normalized.nodeMap.get(selectedTypeId);
  if (!selected || selected.label !== 'JavaType') {
    return { nodes: [], links: [], incomingCount: 0, outgoingCount: 0, selected: null };
  }
  const incoming = normalized.classEdges.filter((edge) => edge.targetId === selectedTypeId);
  const outgoing = normalized.classEdges.filter((edge) => edge.sourceId === selectedTypeId);
  const centerId = `impact:selected:${selectedTypeId}`;
  const nodes = [impactNode(selected, centerId, 'selected', 0, 1)];
  const links = [];

  incoming.forEach((edge, index) => {
    const rawNode = normalized.nodeMap.get(edge.sourceId);
    const id = `impact:incoming:${edge.sourceId}`;
    nodes.push(impactNode(rawNode, id, 'incoming', index, incoming.length));
    links.push({
      id: `${id}|${centerId}`,
      source: id,
      target: centerId,
      type: edge.types.join(', '),
      linkKind: 'impact',
    });
  });
  outgoing.forEach((edge, index) => {
    const rawNode = normalized.nodeMap.get(edge.targetId);
    const id = `impact:outgoing:${edge.targetId}`;
    nodes.push(impactNode(rawNode, id, 'outgoing', index, outgoing.length));
    links.push({
      id: `${centerId}|${id}`,
      source: centerId,
      target: id,
      type: edge.types.join(', '),
      linkKind: 'impact',
    });
  });

  return {
    nodes,
    links,
    incomingCount: incoming.length,
    outgoingCount: outgoing.length,
    selected,
  };
}

const FLOW_STAGE = {
  Controller: 0,
  Service: 1,
  Repository: 2,
  Mapper: 2,
  DatabaseTable: 3,
};

export const FLOW_COLUMNS = [
  { key: 'Controller', label: 'Controller', x: -510 },
  { key: 'Service', label: 'Service', x: -170 },
  { key: 'DataAccess', label: 'Repository / Mapper', x: 170 },
  { key: 'DatabaseTable', label: 'Database', x: 510 },
];

function flowStage(node) {
  return FLOW_STAGE[sourceNodeRole(node)];
}

export function buildLayerFlowGraph(graph) {
  const normalized = normalizedGraph(graph);
  const edgeMap = new Map();
  const addEdge = (sourceId, targetId, types) => {
    const source = normalized.nodeMap.get(sourceId);
    const target = normalized.nodeMap.get(targetId);
    const sourceStage = flowStage(source);
    const targetStage = flowStage(target);
    if (sourceStage == null || targetStage == null || sourceStage > targetStage || sourceId === targetId) return;
    const key = `${sourceId}|${targetId}`;
    if (!edgeMap.has(key)) edgeMap.set(key, { sourceId, targetId, types: new Set() });
    types.forEach((type) => edgeMap.get(key).types.add(type));
  };
  normalized.classEdges.forEach((edge) => addEdge(edge.sourceId, edge.targetId, edge.types));
  normalized.databaseEdges.forEach((edge) => addEdge(edge.sourceId, edge.targetId, edge.types));

  const edges = [...edgeMap.values()].map((edge) => ({ ...edge, types: [...edge.types].sort() }));
  const adjacency = new Map();
  edges.forEach((edge) => {
    if (!adjacency.has(edge.sourceId)) adjacency.set(edge.sourceId, []);
    adjacency.get(edge.sourceId).push(edge.targetId);
  });
  const controllers = normalized.typeNodes.filter((node) => sourceNodeRole(node) === 'Controller');
  const reachable = new Set(controllers.map((node) => node.id));
  const queue = [...reachable];
  while (queue.length > 0) {
    const sourceId = queue.shift();
    (adjacency.get(sourceId) ?? []).forEach((targetId) => {
      if (!reachable.has(targetId)) {
        reachable.add(targetId);
        queue.push(targetId);
      }
    });
  }
  const hasDatabasePath = [...reachable].some((id) => normalized.nodeMap.get(id)?.label === 'DatabaseTable');
  const includedIds = hasDatabasePath
    ? reachable
    : new Set(edges.flatMap((edge) => [edge.sourceId, edge.targetId]));
  const includedEdges = edges.filter((edge) => includedIds.has(edge.sourceId) && includedIds.has(edge.targetId));
  const nodesByStage = [[], [], [], []];
  includedIds.forEach((id) => {
    const node = normalized.nodeMap.get(id);
    const stage = flowStage(node);
    if (node && stage != null) nodesByStage[stage].push(node);
  });
  nodesByStage.forEach((nodes) => nodes.sort((left, right) => (
    sourceNodeDisplayName(left).localeCompare(sourceNodeDisplayName(right))
  )));

  const xByStage = [-510, -170, 170, 510];
  const nodes = nodesByStage.flatMap((stageNodes, stage) => stageNodes.map((rawNode, index) => ({
    ...rawNode,
    rawNode,
    nodeKind: rawNode.label === 'DatabaseTable' ? 'database' : 'flow',
    architectureRole: sourceNodeRole(rawNode),
    packageName: rawNode.label === 'JavaType' ? sourceNodePackage(rawNode) : 'Database',
    flowStage: stage,
    fx: xByStage[stage],
    fy: (index - (stageNodes.length - 1) / 2) * 125,
  })));
  const nodeIds = new Set(nodes.map((node) => node.id));
  const links = includedEdges
    .filter((edge) => nodeIds.has(edge.sourceId) && nodeIds.has(edge.targetId))
    .map((edge) => ({
      id: `${edge.sourceId}|${edge.targetId}`,
      source: edge.sourceId,
      target: edge.targetId,
      type: edge.types.join(', '),
      linkKind: 'flow',
    }));

  return {
    nodes,
    links,
    controllerCount: nodesByStage[0].length,
    serviceCount: nodesByStage[1].length,
    dataAccessCount: nodesByStage[2].length,
    databaseCount: nodesByStage[3].length,
  };
}

function stronglyConnectedComponents(packages, adjacency) {
  let nextIndex = 0;
  const indexByNode = new Map();
  const lowLink = new Map();
  const stack = [];
  const onStack = new Set();
  const components = [];

  const connect = (node) => {
    indexByNode.set(node, nextIndex);
    lowLink.set(node, nextIndex);
    nextIndex += 1;
    stack.push(node);
    onStack.add(node);

    (adjacency.get(node) ?? []).forEach((target) => {
      if (!indexByNode.has(target)) {
        connect(target);
        lowLink.set(node, Math.min(lowLink.get(node), lowLink.get(target)));
      } else if (onStack.has(target)) {
        lowLink.set(node, Math.min(lowLink.get(node), indexByNode.get(target)));
      }
    });

    if (lowLink.get(node) === indexByNode.get(node)) {
      const component = [];
      let member;
      do {
        member = stack.pop();
        onStack.delete(member);
        component.push(member);
      } while (member !== node);
      components.push(component);
    }
  };

  packages.forEach((packageName) => {
    if (!indexByNode.has(packageName)) connect(packageName);
  });
  return components;
}

function findCyclePath(component, adjacency) {
  const allowed = new Set(component);
  for (const start of component) {
    const path = [start];
    const visiting = new Set([start]);
    const search = (current) => {
      for (const target of adjacency.get(current) ?? []) {
        if (!allowed.has(target)) continue;
        if (target === start && path.length > 1) return [...path, start];
        if (visiting.has(target)) continue;
        visiting.add(target);
        path.push(target);
        const result = search(target);
        if (result) return result;
        path.pop();
        visiting.delete(target);
      }
      return null;
    };
    const result = search(start);
    if (result) return result;
  }
  return [...component, component[0]];
}

export function buildPackageDependencyAnalysis(graph) {
  const normalized = normalizedGraph(graph);
  const packages = [...new Set(normalized.typeNodes.map(sourceNodePackage))].sort((left, right) => (
    left.localeCompare(right)
  ));
  const counts = Object.fromEntries(packages.map((source) => [
    source,
    Object.fromEntries(packages.map((target) => [target, 0])),
  ]));
  const relationTypes = {};
  const adjacency = new Map(packages.map((packageName) => [packageName, new Set()]));

  normalized.classEdges.forEach((edge) => {
    const sourcePackage = sourceNodePackage(normalized.nodeMap.get(edge.sourceId));
    const targetPackage = sourceNodePackage(normalized.nodeMap.get(edge.targetId));
    if (!sourcePackage || !targetPackage || sourcePackage === targetPackage) return;
    counts[sourcePackage][targetPackage] += 1;
    adjacency.get(sourcePackage)?.add(targetPackage);
    const key = `${sourcePackage}|${targetPackage}`;
    if (!relationTypes[key]) relationTypes[key] = new Set();
    edge.types.forEach((type) => relationTypes[key].add(type));
  });

  const cycles = stronglyConnectedComponents(packages, adjacency)
    .filter((component) => component.length > 1)
    .map((component, index) => ({
      id: `cycle-${index + 1}`,
      packages: [...component].sort((left, right) => left.localeCompare(right)),
      path: findCyclePath(component, adjacency),
    }))
    .sort((left, right) => right.packages.length - left.packages.length);
  const cyclePackages = new Set(cycles.flatMap((cycle) => cycle.packages));

  return {
    packages,
    counts,
    relationTypes: Object.fromEntries(
      Object.entries(relationTypes).map(([key, types]) => [key, [...types].sort()]),
    ),
    cycles,
    cyclePackages,
    dependencyCount: normalized.classEdges.filter((edge) => (
      sourceNodePackage(normalized.nodeMap.get(edge.sourceId))
      !== sourceNodePackage(normalized.nodeMap.get(edge.targetId))
    )).length,
  };
}
