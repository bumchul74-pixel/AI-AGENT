# ADR-013: Separate read-only Neo4j data explorer

- Status: Accepted
- Date: 2026-08-19

## Context

The Java Source Graph workbench presents a domain-specific projection. Operators also need to inspect arbitrary nodes already stored in Neo4j without coupling the generic browser to Java graph DTOs, filters, or graph visualization behavior.

## Decision

Create a separate `neo4jexplorer` backend boundary and a separate frontend page. The backend uses `Neo4jClient` behind a repository interface, exposes only read operations, identifies nodes by Neo4j `elementId`, masks sensitive property names, and caps relationship details at 500. Node lists use zero-based server-side pagination, an exact optional label filter, a keyword filter, and deterministic ordering by display name plus `elementId`. The frontend follows the project list standard: fetch 30 records initially, request the next page within 80px of the scroll end, deduplicate by `elementId`, and append while retaining the existing rows.

## Consequences

The generic explorer can evolve without changing Java Source Graph contracts. It does not provide mutation or arbitrary Cypher execution. `elementId` is suitable for navigating the current database but must not be treated as a portable business identifier across database recreation or migration.