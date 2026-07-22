from contextlib import asynccontextmanager
import asyncio

from json import JSONDecodeError, loads

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile

from app.directory_watcher import DirectoryIngestWatcher
from app.file_text_extractor import extract_text_from_upload
from app.schemas import (ChunkBatchRequest, ChunkBatchResponse, DocumentIngestRequest,
                         RagChunkResult, RagSearchRequest, RagSearchResponse, RagStatsResponse,
                         StoredChunkBatchRequest)
from app.settings import load_watch_settings
from app.vector_store import LocalVectorStore

vector_store = LocalVectorStore()


@asynccontextmanager
async def lifespan(app: FastAPI):
    watch_settings = load_watch_settings()
    watcher = None
    watcher_task = None

    if watch_settings.enabled:
        watcher = DirectoryIngestWatcher(watch_settings)
        watcher_task = asyncio.create_task(watcher.run())

    try:
        yield
    finally:
        if watcher is not None and watcher_task is not None:
            watcher.stop()
            await watcher_task


app = FastAPI(title="AI-AGENT Local RAG Server", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/documents")
def ingest_document(request: DocumentIngestRequest) -> dict[str, int]:
    stored_count = vector_store.add_document(
        source=request.source,
        content=request.content,
        chunk_size=request.chunk_size,
        overlap=request.overlap,
        project_id=request.project_id,
        file_path=request.file_path,
        file_hash=request.file_hash,
        entity_ids=request.entity_ids,
        document_id=request.document_id,
        symbol=request.symbol,
        metadata=request.metadata,
    )
    return {"stored_count": stored_count}

@app.post("/api/documents/upload")
async def upload_document(
    source: str = Form(...),
    file: UploadFile = File(...),
    chunk_size: int = Form(default=1200),
    overlap: int = Form(default=150),
    project_id: str = Form(default="default"),
    file_path: str = Form(default=""),
    file_hash: str = Form(default=""),
    entity_ids: str = Form(default="[]"),
    document_id: int | None = Form(default=None),
    symbol: str = Form(default=""),
    metadata: str = Form(default="{}"),
) -> dict[str, int]:
    if not source.strip():
        raise HTTPException(status_code=400, detail="source is required")

    try:
        content = await extract_text_from_upload(file)
    except ValueError as exception:
        raise HTTPException(status_code=400, detail=str(exception)) from exception

    stored_count = vector_store.add_document(
        source=source,
        content=content,
        chunk_size=max(200, min(8000, chunk_size)),
        overlap=max(0, min(2000, overlap)),
        project_id=project_id.strip(),
        file_path=file_path.strip() or file.filename or "",
        file_hash=file_hash.strip(),
        entity_ids=parse_entity_ids(entity_ids),
        document_id=document_id,
        symbol=symbol.strip(),
        metadata=parse_metadata(metadata),
    )
    return {"stored_count": stored_count}


def parse_entity_ids(value: str) -> list[str]:
    try:
        loaded = loads(value)
    except (JSONDecodeError, TypeError):
        return []
    if not isinstance(loaded, list):
        return []
    return [str(item).strip() for item in loaded if str(item).strip()]


def parse_metadata(value: str) -> dict[str, object]:
    try:
        loaded = loads(value)
    except (JSONDecodeError, TypeError):
        return {}
    return loaded if isinstance(loaded, dict) else {}


@app.delete("/api/documents/source")
def delete_document_source(source: str) -> dict[str, int]:
    if not source.strip():
        raise HTTPException(status_code=400, detail="source is required")

    deleted_count = vector_store.delete_source(source)
    return {"deleted_count": deleted_count}


@app.get("/api/sources")
def sources() -> list[dict[str, object]]:
    return vector_store.list_sources()


@app.post("/api/search", response_model=RagSearchResponse)
async def search(request: Request) -> RagSearchResponse:
    try:
        payload = await request.json()
    except JSONDecodeError:
        return RagSearchResponse(documents=[])

    if not isinstance(payload, dict):
        return RagSearchResponse(documents=[])

    query = payload.get("query") or payload.get("message") or ""
    if not query.strip():
        return RagSearchResponse(documents=[])

    top_k = payload.get("top_k", 5)
    try:
        top_k = max(1, min(20, int(top_k)))
    except (TypeError, ValueError):
        top_k = 5

    search_request = RagSearchRequest(
        query=query,
        top_k=top_k,
        projectId=str(payload.get("projectId", "")).strip(),
    )
    chunks = vector_store.search_chunks(
        query=search_request.query,
        top_k=search_request.top_k,
        project_id=search_request.projectId,
    )
    documents = [f"[source: {chunk['sourceKey']}]\n{chunk['content']}" for chunk in chunks]
    return RagSearchResponse(
        documents=documents,
        chunks=[RagChunkResult(**chunk) for chunk in chunks],
    )


@app.post("/api/chunks/by-ids", response_model=ChunkBatchResponse)
def chunks_by_ids(request: ChunkBatchRequest) -> ChunkBatchResponse:
    chunks = vector_store.find_chunks(request.chunkIds)
    return ChunkBatchResponse(chunks=[RagChunkResult(**chunk) for chunk in chunks])


@app.post("/api/chunks")
def store_chunks(request: StoredChunkBatchRequest) -> dict[str, int]:
    stored_count = vector_store.add_chunks([chunk.model_dump() for chunk in request.chunks])
    return {"stored_count": stored_count}


@app.get("/api/stats", response_model=RagStatsResponse)
def stats() -> RagStatsResponse:
    return RagStatsResponse(java_file_count=vector_store.java_file_count())
