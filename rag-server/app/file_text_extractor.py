from __future__ import annotations

from io import BytesIO
from pathlib import Path

from charset_normalizer import from_bytes
from fastapi import UploadFile

TEXT_EXTENSIONS = {
    ".txt",
    ".md",
    ".java",
    ".kt",
    ".xml",
    ".yml",
    ".yaml",
    ".json",
    ".sql",
    ".properties",
    ".gradle",
    ".kts",
    ".py",
    ".js",
    ".jsx",
    ".ts",
    ".tsx",
    ".html",
    ".css",
}


async def extract_text_from_upload(file: UploadFile) -> str:
    raw_content = await file.read()
    if not raw_content:
        raise ValueError("Uploaded file is empty.")

    suffix = Path(file.filename or "").suffix.lower()
    content_type = (file.content_type or "").lower()

    if suffix in TEXT_EXTENSIONS or content_type.startswith("text/"):
        return decode_text(raw_content)
    if suffix == ".pdf":
        return extract_pdf(raw_content)
    if suffix == ".docx":
        return extract_docx(raw_content)

    raise ValueError(f"Unsupported file type: {suffix or content_type or 'unknown'}")


def decode_text(raw_content: bytes) -> str:
    match = from_bytes(raw_content).best()
    if match is not None:
        return str(match)
    return raw_content.decode("utf-8", errors="replace")


def extract_pdf(raw_content: bytes) -> str:
    try:
        from pypdf import PdfReader
    except ImportError as exception:
        raise ValueError("PDF extraction requires pypdf to be installed.") from exception

    reader = PdfReader(BytesIO(raw_content))
    pages = [page.extract_text() or "" for page in reader.pages]
    content = "\n\n".join(page for page in pages if page.strip())
    if not content.strip():
        raise ValueError("No extractable text found in PDF file.")
    return content


def extract_docx(raw_content: bytes) -> str:
    try:
        from docx import Document
    except ImportError as exception:
        raise ValueError("DOCX extraction requires python-docx to be installed.") from exception

    document = Document(BytesIO(raw_content))
    paragraphs = [paragraph.text for paragraph in document.paragraphs if paragraph.text.strip()]
    content = "\n\n".join(paragraphs)
    if not content.strip():
        raise ValueError("No extractable text found in DOCX file.")
    return content
