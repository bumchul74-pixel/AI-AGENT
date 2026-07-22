from __future__ import annotations

from typing import Any, TypedDict

from app.vector_store import LocalVectorStore


class RagSearchResult(TypedDict):
    documents: list[str]


class RagIngestResult(TypedDict):
    stored_count: int


class RagStatsResult(TypedDict):
    java_file_count: int


def rag_search(
    vector_store: LocalVectorStore,
    query: str,
    top_k: int = 5,
) -> RagSearchResult:
    normalized_query = query.strip()
    if not normalized_query:
        return {"documents": []}

    return {
        "documents": vector_store.search(
            query=normalized_query,
            top_k=normalize_top_k(top_k),
        )
    }


def rag_ingest_document(
    vector_store: LocalVectorStore,
    source: str,
    content: str,
    chunk_size: int = 1200,
    overlap: int = 150,
    project_id: str = "",
    file_path: str = "",
    file_hash: str = "",
    entity_ids: list[str] | None = None,
    document_id: int | None = None,
    symbol: str = "",
    metadata: dict[str, Any] | None = None,
) -> RagIngestResult:
    stored_count = vector_store.add_document(
        source=source.strip(),
        content=content,
        chunk_size=normalize_chunk_size(chunk_size),
        overlap=normalize_overlap(overlap),
        project_id=project_id.strip(),
        file_path=file_path.strip(),
        file_hash=file_hash.strip(),
        entity_ids=entity_ids or [],
        document_id=document_id,
        symbol=symbol.strip(),
        metadata=metadata or {},
    )
    return {"stored_count": stored_count}


def rag_stats(vector_store: LocalVectorStore) -> RagStatsResult:
    return {"java_file_count": vector_store.java_file_count()}


def normalize_top_k(top_k: int) -> int:
    return max(1, min(20, int(top_k)))


def normalize_chunk_size(chunk_size: int) -> int:
    return max(200, min(8000, int(chunk_size)))


def normalize_overlap(overlap: int) -> int:
    return max(0, min(2000, int(overlap)))
