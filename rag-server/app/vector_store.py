from __future__ import annotations

import hashlib
import json
import math
import re
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


STORE_PATH = Path(__file__).resolve().parent.parent / "data" / "vector_store.json"
VECTOR_SIZE = 512


@dataclass
class StoredChunk:
    content: str
    embedding: list[float]
    chunk_id: str = ""
    document_id: int | None = None
    source_key: str = ""
    project_id: str = ""
    file_path: str = ""
    entity_ids: list[str] = field(default_factory=list)
    symbol: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)
    file_hash: str = ""


class LocalVectorStore:
    def __init__(self, store_path: Path = STORE_PATH) -> None:
        self.store_path = store_path
        self.store_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self.chunks = self._load()

    def add_document(
        self,
        source: str,
        content: str,
        chunk_size: int,
        overlap: int,
        *,
        entity_ids: list[str] | None = None,
        file_hash: str = "",
        project_id: str = "",
        file_path: str = "",
        document_id: int | None = None,
        symbol: str = "",
        metadata: dict[str, Any] | None = None,
    ) -> int:
        resolved_document_id = document_id if document_id is not None else parse_document_id(source)
        chunks = split_text(content, chunk_size=chunk_size, overlap=overlap)
        stored_chunks = [
            StoredChunk(
                content=chunk,
                embedding=embed_text(chunk),
                chunk_id=chunk_id(source, index),
                document_id=resolved_document_id,
                source_key=source,
                project_id=project_id,
                file_path=file_path,
                entity_ids=list(entity_ids or []),
                symbol=symbol,
                metadata=dict(metadata or {}),
                file_hash=file_hash,
            )
            for index, chunk in enumerate(chunks)
            if chunk.strip()
        ]
        with self._lock:
            self.chunks.extend(stored_chunks)
            self._save()
        return len(stored_chunks)

    def delete_source(self, source: str) -> int:
        with self._lock:
            before_count = len(self.chunks)
            self.chunks = [chunk for chunk in self.chunks if chunk.source_key != source]
            deleted_count = before_count - len(self.chunks)
            if deleted_count > 0:
                self._save()
        return deleted_count

    def add_chunks(self, chunks: list[dict[str, Any]]) -> int:
        stored_chunks = [
            StoredChunk(
                content=str(chunk["content"]),
                embedding=embed_text(str(chunk["content"])),
                chunk_id=str(chunk["chunkId"]),
                document_id=chunk.get("documentId"),
                source_key=str(chunk["sourceKey"]),
                project_id=str(chunk.get("projectId", "")),
                file_path=str(chunk.get("filePath", "")),
                entity_ids=list(chunk.get("entityIds", [])),
                symbol=str(chunk.get("symbol", "")),
                metadata=dict(chunk.get("metadata", {})),
                file_hash=str(chunk.get("fileHash", "")),
            )
            for chunk in chunks
            if str(chunk.get("content", "")).strip()
        ]
        chunk_ids = {chunk.chunk_id for chunk in stored_chunks}
        with self._lock:
            self.chunks = [chunk for chunk in self.chunks if chunk.chunk_id not in chunk_ids]
            self.chunks.extend(stored_chunks)
            if stored_chunks:
                self._save()
        return len(stored_chunks)

    def search_chunks(self, query: str, top_k: int, project_id: str = "") -> list[dict[str, Any]]:
        with self._lock:
            chunks = list(self.chunks)

        normalized_project_id = project_id.strip()
        if normalized_project_id:
            chunks = [chunk for chunk in chunks if chunk.project_id == normalized_project_id]

        if not chunks:
            return []

        query_vector = embed_text(query)
        scored_chunks = sorted(
            (
                (cosine_similarity(query_vector, chunk.embedding), chunk)
                for chunk in chunks
            ),
            key=lambda item: item[0],
            reverse=True,
        )
        return [
            search_result_payload(chunk, score)
            for score, chunk in scored_chunks[:top_k]
            if score > 0
        ]

    def search(self, query: str, top_k: int, project_id: str = "") -> list[str]:
        return [
            f"[source: {chunk['sourceKey']}]\n{chunk['content']}"
            for chunk in self.search_chunks(query, top_k, project_id)
        ]

    def find_chunks(self, chunk_ids: list[str]) -> list[dict[str, Any]]:
        requested = set(chunk_ids)
        if not requested:
            return []
        with self._lock:
            chunks_by_id = {chunk.chunk_id: chunk for chunk in self.chunks if chunk.chunk_id in requested}
        return [
            search_result_payload(chunks_by_id[chunk_id], 1.0)
            for chunk_id in chunk_ids
            if chunk_id in chunks_by_id
        ]

    def java_file_count(self) -> int:
        with self._lock:
            sources = {chunk.source_key for chunk in self.chunks}

        return sum(1 for source in sources if source.lower().endswith(".java"))

    def list_sources(self) -> list[dict[str, Any]]:
        with self._lock:
            chunks = list(self.chunks)

        sources: dict[str, dict[str, Any]] = {}
        for chunk in chunks:
            source = sources.setdefault(chunk.source_key, {
                "sourceKey": chunk.source_key,
                "documentId": chunk.document_id,
                "projectId": chunk.project_id,
                "filePath": chunk.file_path,
                "fileHash": chunk.file_hash,
                "chunkCount": 0,
            })
            source["chunkCount"] += 1
            if not source["filePath"] and chunk.file_path:
                source["filePath"] = chunk.file_path
            if not source["projectId"] and chunk.project_id:
                source["projectId"] = chunk.project_id

        return sorted(sources.values(), key=lambda item: item["sourceKey"].lower())

    def _load(self) -> list[StoredChunk]:
        if not self.store_path.exists():
            return []

        raw_chunks = json.loads(self.store_path.read_text(encoding="utf-8"))
        return [stored_chunk_from_payload(chunk) for chunk in raw_chunks]

    def _save(self) -> None:
        payload = [stored_chunk_payload(chunk) for chunk in self.chunks]
        self.store_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def split_text(content: str, chunk_size: int, overlap: int) -> list[str]:
    normalized = re.sub(r"\n{3,}", "\n\n", content.strip())
    if not normalized:
        return []

    chunks: list[str] = []
    start = 0
    step = max(1, chunk_size - overlap)

    while start < len(normalized):
        chunks.append(normalized[start:start + chunk_size])
        start += step

    return chunks


def chunk_id(source: str, index: int) -> str:
    return f"{source}:chunk:{index}"


def parse_document_id(source: str) -> int | None:
    prefix = "document:"
    if not source.startswith(prefix):
        return None
    value = source[len(prefix):]
    return int(value) if value.isdigit() else None


def stored_chunk_from_payload(payload: dict[str, Any]) -> StoredChunk:
    return StoredChunk(
        content=str(payload.get("content", "")),
        embedding=list(payload.get("embedding", payload.get("vector", []))),
        chunk_id=str(payload.get("chunkId", payload.get("chunk_id", ""))),
        document_id=payload.get("documentId", payload.get("document_id")),
        source_key=str(payload.get("sourceKey", payload.get("source_key", payload.get("source", "")))),
        project_id=str(payload.get("projectId", payload.get("project_id", ""))),
        file_path=str(payload.get("filePath", payload.get("file_path", ""))),
        entity_ids=list(payload.get("entityIds", payload.get("entity_ids", []))),
        symbol=str(payload.get("symbol", "")),
        metadata=dict(payload.get("metadata", {})),
        file_hash=str(payload.get("fileHash", payload.get("file_hash", ""))),
    )


def stored_chunk_payload(chunk: StoredChunk) -> dict[str, Any]:
    return {
        "chunkId": chunk.chunk_id,
        "documentId": chunk.document_id,
        "sourceKey": chunk.source_key,
        "projectId": chunk.project_id,
        "filePath": chunk.file_path,
        "entityIds": chunk.entity_ids,
        "symbol": chunk.symbol,
        "content": chunk.content,
        "embedding": chunk.embedding,
        "metadata": chunk.metadata,
        "fileHash": chunk.file_hash,
    }


def search_result_payload(chunk: StoredChunk, score: float) -> dict[str, Any]:
    return {
        "chunkId": chunk.chunk_id,
        "sourceKey": chunk.source_key,
        "content": chunk.content,
        "entityIds": chunk.entity_ids,
        "score": score,
        "filePath": chunk.file_path,
        "metadata": chunk.metadata,
    }


def embed_text(text: str) -> list[float]:
    vector = [0.0] * VECTOR_SIZE
    tokens = tokenize(text)

    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % VECTOR_SIZE
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[index] += sign

    return normalize(vector)


def tokenize(text: str) -> list[str]:
    return re.findall(r"[A-Za-z0-9_\uac00-\ud7a3]+", text.lower())


def normalize(vector: list[float]) -> list[float]:
    magnitude = math.sqrt(sum(value * value for value in vector))
    if magnitude == 0:
        return vector
    return [value / magnitude for value in vector]


def cosine_similarity(left: list[float], right: list[float]) -> float:
    return sum(left_value * right_value for left_value, right_value in zip(left, right))
