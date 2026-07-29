from __future__ import annotations

import base64
import binascii
import os
from pathlib import Path
from threading import Lock
from typing import Any

from mcp.server.fastmcp import FastMCP


RAG_SERVER_DIR = Path(__file__).resolve().parents[2]
PROJECT_DIR = RAG_SERVER_DIR.parent
DEFAULT_MODEL_DIR = RAG_SERVER_DIR / "data" / "easyocr-models"
DEFAULT_ALLOWED_DIRS = (RAG_SERVER_DIR / "inbox", PROJECT_DIR / "uploads")
SUPPORTED_IMAGE_EXTENSIONS = {
    ".bmp", ".gif", ".jpeg", ".jpg", ".png", ".tif", ".tiff", ".webp"
}
SUPPORTED_DOCUMENT_EXTENSIONS = SUPPORTED_IMAGE_EXTENSIONS | {".pdf"}
MAX_IMAGE_BYTES = 20 * 1024 * 1024
MAX_PDF_PAGES = 20

_reader: Any | None = None
_reader_lock = Lock()


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _languages() -> list[str]:
    configured = os.getenv("EASYOCR_LANGUAGES", "ko,en")
    languages = [value.strip() for value in configured.split(",") if value.strip()]
    return languages or ["ko", "en"]


def _model_directory() -> Path:
    configured = os.getenv("EASYOCR_MODEL_DIR")
    path = Path(configured).expanduser() if configured else DEFAULT_MODEL_DIR
    path = path.resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def _allowed_directories() -> tuple[Path, ...]:
    configured = os.getenv("EASYOCR_ALLOWED_DIRS")
    values = configured.split(os.pathsep) if configured else DEFAULT_ALLOWED_DIRS
    return tuple(Path(value).expanduser().resolve() for value in values if str(value).strip())


def _resolve_image_path(file_path: str) -> Path:
    if not file_path or not file_path.strip():
        raise ValueError("file_path is required.")

    path = Path(file_path.strip()).expanduser()
    if not path.is_absolute():
        path = (RAG_SERVER_DIR / path).resolve()
    else:
        path = path.resolve()

    if not any(path == directory or path.is_relative_to(directory)
               for directory in _allowed_directories()):
        raise ValueError("Image path is outside EASYOCR_ALLOWED_DIRS.")
    if path.suffix.lower() not in SUPPORTED_IMAGE_EXTENSIONS:
        raise ValueError(f"Unsupported image type: {path.suffix or 'unknown'}")
    if not path.is_file():
        raise ValueError(f"Image file was not found: {path}")
    if path.stat().st_size > MAX_IMAGE_BYTES:
        raise ValueError("Image exceeds the 20 MB size limit.")
    return path


def _decode_base64_image(image_base64: str) -> bytes:
    if not image_base64 or not image_base64.strip():
        raise ValueError("image_base64 is required.")

    encoded = image_base64.strip()
    if encoded.startswith("data:"):
        header, separator, encoded = encoded.partition(",")
        if not separator or not header.lower().startswith("data:image/"):
            raise ValueError("Only image data URLs are supported.")

    try:
        raw_image = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exception:
        raise ValueError("image_base64 is not valid Base64 image data.") from exception

    if not raw_image:
        raise ValueError("Decoded image is empty.")
    if len(raw_image) > MAX_IMAGE_BYTES:
        raise ValueError("Image exceeds the 20 MB size limit.")
    return raw_image


def _decode_base64_document(file_base64: str) -> bytes:
    if not file_base64 or not file_base64.strip():
        raise ValueError("file_base64 is required.")

    encoded = file_base64.strip()
    if encoded.startswith("data:"):
        _, separator, encoded = encoded.partition(",")
        if not separator:
            raise ValueError("Invalid Base64 data URL.")

    try:
        raw_document = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exception:
        raise ValueError("file_base64 is not valid Base64 data.") from exception

    if not raw_document:
        raise ValueError("Decoded document is empty.")
    if len(raw_document) > MAX_IMAGE_BYTES:
        raise ValueError("Document exceeds the 20 MB size limit.")
    return raw_document


def _get_reader() -> Any:
    global _reader
    if _reader is not None:
        return _reader

    with _reader_lock:
        if _reader is None:
            import easyocr

            _reader = easyocr.Reader(
                _languages(),
                gpu=_env_bool("EASYOCR_GPU", False),
                model_storage_directory=str(_model_directory()),
                download_enabled=_env_bool("EASYOCR_DOWNLOAD_ENABLED", True),
                verbose=False,
            )
    return _reader


def _normalize_results(results: list[Any]) -> dict[str, Any]:
    regions: list[dict[str, Any]] = []
    for result in results:
        if not isinstance(result, (list, tuple)) or len(result) < 2:
            continue
        box, text = result[0], str(result[1])
        confidence = float(result[2]) if len(result) > 2 else None
        regions.append({
            "text": text,
            "confidence": confidence,
            "box": [[float(point[0]), float(point[1])] for point in box],
        })

    return {
        "text": chr(10).join(region["text"] for region in regions),
        "regions": regions,
        "region_count": len(regions),
        "languages": _languages(),
    }


def _read_image(image: str | bytes) -> dict[str, Any]:
    results = _get_reader().readtext(image, detail=1)
    return _normalize_results(results)


def _read_pdf(document: bytes, max_pages: int) -> dict[str, Any]:
    try:
        import fitz
    except ImportError as exception:
        raise ValueError("PDF OCR requires PyMuPDF to be installed.") from exception

    page_limit = min(max(1, int(max_pages)), MAX_PDF_PAGES)
    pages: list[dict[str, Any]] = []
    with fitz.open(stream=document, filetype="pdf") as pdf:
        if pdf.page_count > page_limit:
            raise ValueError(
                f"PDF has {pdf.page_count} pages; the configured limit is {page_limit}.")
        for page_number, page in enumerate(pdf, start=1):
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            result = _read_image(pixmap.tobytes("png"))
            pages.append({
                "page": page_number,
                "text": result["text"],
                "regions": result["regions"],
                "region_count": result["region_count"],
            })

    text = "\n\n".join(
        f"[Page {page['page']}]\n{page['text']}" for page in pages if page["text"].strip()
    )
    return {
        "text": text,
        "pages": pages,
        "page_count": len(pages),
        "region_count": sum(page["region_count"] for page in pages),
        "languages": _languages(),
    }


ocr_server = FastMCP(
    "easyocr",
    instructions=(
        "Extract Korean and English text from PDF and image files or Base64 data. "
        "File access is restricted to EASYOCR_ALLOWED_DIRS."
    ),
    host=os.getenv("EASYOCR_MCP_HOST", "127.0.0.1"),
    port=int(os.getenv("EASYOCR_MCP_PORT", "8001")),
    streamable_http_path="/ocr",
)


@ocr_server.tool(description="Extract text from an image file in an allowed directory.")
def ocr_image_file(file_path: str) -> dict[str, Any]:
    path = _resolve_image_path(file_path)
    result = _read_image(str(path))
    result["source"] = str(path)
    return result


@ocr_server.tool(description="Extract text from a Base64-encoded image or image data URL.")
def ocr_image_base64(image_base64: str) -> dict[str, Any]:
    result = _read_image(_decode_base64_image(image_base64))
    result["source"] = "base64"
    return result


@ocr_server.tool(description="Extract text from a Base64-encoded PDF or image document.")
def ocr_document_base64(
        file_base64: str, file_name: str, max_pages: int = 10) -> dict[str, Any]:
    extension = Path(file_name or "").suffix.lower()
    if extension not in SUPPORTED_DOCUMENT_EXTENSIONS:
        raise ValueError(f"Unsupported OCR document type: {extension or 'unknown'}")

    document = _decode_base64_document(file_base64)
    result = (_read_pdf(document, max_pages)
              if extension == ".pdf" else _read_image(document))
    result["source"] = file_name
    result["file_type"] = extension.lstrip(".")
    return result


def main() -> None:
    transport = os.getenv("EASYOCR_MCP_TRANSPORT", "stdio").strip().lower()
    if transport not in {"stdio", "sse", "streamable-http"}:
        raise ValueError("EASYOCR_MCP_TRANSPORT must be stdio, sse, or streamable-http.")
    ocr_server.run(transport=transport)


if __name__ == "__main__":
    main()
