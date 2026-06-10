from pydantic import BaseModel, Field


class DocumentIngestRequest(BaseModel):
    source: str = Field(min_length=1)
    content: str = Field(min_length=1)
    chunk_size: int = Field(default=1200, ge=200, le=8000)
    overlap: int = Field(default=150, ge=0, le=2000)


class RagSearchRequest(BaseModel):
    query: str = Field(min_length=1)
    top_k: int = Field(default=5, ge=1, le=20)


class RagSearchResponse(BaseModel):
    documents: list[str]
