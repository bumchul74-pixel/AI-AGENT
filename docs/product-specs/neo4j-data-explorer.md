# Neo4j Data Explorer

## Goal

Provide a read-only Neo4j node catalog and node detail view that is independent from the Java Source Graph workbench.

## User flow

1. Open `Data Explorer > Neo4j Data Explorer`.
2. Optionally filter by an exact label and a keyword searched across labels and node properties.
3. Browse the node list; after the initial 30 records, reaching the list-end threshold loads the next server page and appends it.
4. Select a node to open the shared modal layer and inspect all returned properties and up to 500 adjacent relationships.
5. Select a related node to replace the detail inside the same modal layer.
6. Select `라벨 관계 그래프` to open a graph of labels and the relationship types observed between them.

## API contract

- `GET /api/neo4j-explorer/nodes?label=&keyword=&page=0&size=30`
- `GET /api/neo4j-explorer/nodes/{elementId}`
- `GET /api/neo4j-explorer/schema`

The API uses zero-based server pagination with `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`. The UI requests 30 records initially and fetches the next server page when the scroll position is within 80px of the list end. New records are deduplicated by `elementId` and appended to the existing list. Results are ordered by the derived display name and then `elementId` so navigation is deterministic.

The schema response aggregates actual stored data. Each graph node represents a label and includes its node count. Each directed graph link represents a source-label, relationship-type, and target-label tuple and includes its relationship count. Multi-label nodes contribute to every applicable label pair. The response contains at most 100 labels and 500 aggregated relationships and reports `labelsTruncated` and `relationshipsTruncated` when those limits are exceeded.

## Safety and limits

- The feature exposes no create, update, or delete endpoint.
- Property names containing password, secret, token, credential, apiKey, or api_key are masked.
- A detail response returns at most 500 relationships; `relationshipCount` remains the total count.
- The label relationship graph is aggregate-only and bounded; it does not expose node properties or accept Cypher.
- Neo4j connection failures are reported through the shared API error and Toast path.

## Acceptance criteria

- The first 30 nodes are shown initially; approaching the list end appends the next server page without replacing already loaded nodes.
- The node list uses the shared `ScrollableListLayout` and `DataTable`; its opaque column header remains fixed and row content never shows through or overlaps the header while rows scroll.
- Selecting a node opens the shared `Modal`; the fixed modal header shows the node name and the independently scrolling body shows labels, properties, relationship direction, type, properties, and the connected node.
- Selecting a connected node keeps the modal open and replaces its content with that node detail.
- Empty, loading, failure, and responsive layouts are explicit.
- The label graph opens from an explicit button, shows label names, node counts, relationship types and counts, and identifies truncated results.