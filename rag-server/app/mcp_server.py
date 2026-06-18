from __future__ import annotations

from mcp.server.fastmcp import FastMCP

from app.rag_tools import rag_ingest_document, rag_search, rag_stats
from app.vector_store import LocalVectorStore


mcp = FastMCP("AI-AGENT RAG MCP Server")
vector_store = LocalVectorStore()


@mcp.tool(name="rag_search")
def rag_search_tool(query: str, top_k: int = 5) -> dict[str, list[str]]:
    """Search indexed standard documents and source code."""
    return rag_search(vector_store, query=query, top_k=top_k)


@mcp.tool(name="rag_ingest_document")
def rag_ingest_document_tool(
    source: str,
    content: str,
    chunk_size: int = 1200,
    overlap: int = 150,
) -> dict[str, int]:
    """Index a document or source file into the local RAG store."""
    return rag_ingest_document(
        vector_store,
        source=source,
        content=content,
        chunk_size=chunk_size,
        overlap=overlap,
    )


@mcp.tool(name="rag_stats")
def rag_stats_tool() -> dict[str, int]:
    """Return local RAG store statistics."""
    return rag_stats(vector_store)


if __name__ == "__main__":
    mcp.run(transport="stdio")
