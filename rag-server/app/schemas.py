from pydantic import BaseModel, Field


class DocumentIngestRequest(BaseModel):
    source: str = Field(min_length=1)
    content: str = Field(min_length=1)
    document_id: int | None = None
    project_id: str = "default"
    file_path: str = ""
    file_hash: str = ""
    entity_ids: list[str] = Field(default_factory=list)
    symbol: str = ""
    metadata: dict[str, object] = Field(default_factory=dict)
    chunk_size: int = Field(default=1200, ge=200, le=8000)
    overlap: int = Field(default=150, ge=0, le=2000)


class RagSearchRequest(BaseModel):
    query: str = Field(min_length=1)
    top_k: int = Field(default=5, ge=1, le=20)


class RagChunkResult(BaseModel):
    chunkId: str
    sourceKey: str
    content: str
    entityIds: list[str] = Field(default_factory=list)
    score: float = 0.0
    filePath: str = ""
    metadata: dict[str, object] = Field(default_factory=dict)


class RagSearchResponse(BaseModel):
    documents: list[str]
    chunks: list[RagChunkResult] = Field(default_factory=list)


class ChunkBatchRequest(BaseModel):
    chunkIds: list[str] = Field(default_factory=list, max_length=200)


class ChunkBatchResponse(BaseModel):
    chunks: list[RagChunkResult] = Field(default_factory=list)


class RagStatsResponse(BaseModel):
    java_file_count: int
