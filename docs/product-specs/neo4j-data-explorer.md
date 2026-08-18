# Neo4j Data Explorer

## Goal

Provide a read-only Neo4j node catalog and node detail view that is independent from the Java Source Graph workbench.

## User flow

1. Open `Operations > Neo4j Data Explorer`.
2. Optionally filter by an exact label and a keyword searched across labels and node properties.
3. Browse the node list; after the initial 30 records, reaching the list-end threshold loads the next server page and appends it.
4. Select a node to inspect all returned properties and up to 500 adjacent relationships.
5. Select a related node to continue browsing its detail.

## API contract

- `GET /api/neo4j-explorer/nodes?label=&keyword=&page=0&size=30`
- `GET /api/neo4j-explorer/nodes/{elementId}`

The API uses zero-based server pagination with `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`. The UI requests 30 records initially and fetches the next server page when the scroll position is within 80px of the list end. New records are deduplicated by `elementId` and appended to the existing list. Results are ordered by the derived display name and then `elementId` so navigation is deterministic.

## Safety and limits

- The feature exposes no create, update, or delete endpoint.
- Property names containing password, secret, token, credential, apiKey, or api_key are masked.
- A detail response returns at most 500 relationships; `relationshipCount` remains the total count.
- Neo4j connection failures are reported through the shared API error and Toast path.

## Acceptance criteria

- The first 30 nodes are shown initially; approaching the list end appends the next server page without replacing already loaded nodes.
- The node list uses the shared `ScrollableListLayout` and `DataTable`; its opaque column header remains fixed and row content never shows through or overlaps the header while rows scroll.
- Selecting a node displays labels, properties, relationship direction, type, properties, and the connected node.
- Selecting a connected node opens that node detail.
- Empty, loading, failure, and responsive layouts are explicit.