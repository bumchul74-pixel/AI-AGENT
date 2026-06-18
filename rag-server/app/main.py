from contextlib import asynccontextmanager
import asyncio

from json import JSONDecodeError

from fastapi import FastAPI, Request

from app.directory_watcher import DirectoryIngestWatcher
from app.schemas import DocumentIngestRequest, RagSearchRequest, RagSearchResponse, RagStatsResponse
from app.settings import load_watch_settings
from app.vector_store import LocalVectorStore

vector_store = LocalVectorStore()


@asynccontextmanager
async def lifespan(app: FastAPI):
    watch_settings = load_watch_settings()
    watcher = None
    watcher_task = None

    if watch_settings.enabled:
        watcher = DirectoryIngestWatcher(watch_settings, vector_store)
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
    )
    return {"stored_count": stored_count}


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
    )
    documents = vector_store.search(query=search_request.query, top_k=search_request.top_k)
    return RagSearchResponse(documents=documents)


@app.get("/api/stats", response_model=RagStatsResponse)
def stats() -> RagStatsResponse:
    return RagStatsResponse(java_file_count=vector_store.java_file_count())
