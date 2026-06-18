from __future__ import annotations

import hashlib
import json
import math
import re
import threading
from dataclasses import asdict, dataclass
from pathlib import Path


STORE_PATH = Path(__file__).resolve().parent.parent / "data" / "vector_store.json"
VECTOR_SIZE = 512


@dataclass
class StoredChunk:
    source: str
    content: str
    vector: list[float]


class LocalVectorStore:
    def __init__(self, store_path: Path = STORE_PATH) -> None:
        self.store_path = store_path
        self.store_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self.chunks = self._load()

    def add_document(self, source: str, content: str, chunk_size: int, overlap: int) -> int:
        chunks = split_text(content, chunk_size=chunk_size, overlap=overlap)
        stored_chunks = [
            StoredChunk(source=source, content=chunk, vector=embed_text(chunk))
            for chunk in chunks
            if chunk.strip()
        ]
        with self._lock:
            self.chunks.extend(stored_chunks)
            self._save()
        return len(stored_chunks)

    def search(self, query: str, top_k: int) -> list[str]:
        with self._lock:
            chunks = list(self.chunks)

        if not chunks:
            return []

        query_vector = embed_text(query)
        scored_chunks = sorted(
            (
                (cosine_similarity(query_vector, chunk.vector), chunk)
                for chunk in chunks
            ),
            key=lambda item: item[0],
            reverse=True,
        )
        return [
            f"[source: {chunk.source}]\n{chunk.content}"
            for score, chunk in scored_chunks[:top_k]
            if score > 0
        ]

    def java_file_count(self) -> int:
        with self._lock:
            sources = {chunk.source for chunk in self.chunks}

        return sum(1 for source in sources if source.lower().endswith(".java"))

    def _load(self) -> list[StoredChunk]:
        if not self.store_path.exists():
            return []

        raw_chunks = json.loads(self.store_path.read_text(encoding="utf-8"))
        return [StoredChunk(**chunk) for chunk in raw_chunks]

    def _save(self) -> None:
        payload = [asdict(chunk) for chunk in self.chunks]
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


