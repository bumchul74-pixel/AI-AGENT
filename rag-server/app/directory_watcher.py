from __future__ import annotations

import asyncio
import json
import logging
import mimetypes
import os
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from typing import Any

from app.settings import WatchSettings


LOGGER = logging.getLogger("rag.directory_watcher")

SUPPORTED_EXTENSIONS = {
    ".java",
    ".kt",
    ".xml",
    ".sql",
    ".yml",
    ".yaml",
    ".md",
    ".js",
    ".jsx",
    ".ts",
    ".tsx",
}
DOCUMENT_UPLOAD_TIMEOUT_SECONDS = 60
PROCESSING_LOCK_SUFFIX = ".processing"
PROCESSING_LOCK_MAX_AGE_SECONDS = 3600


class DirectoryIngestWatcher:
    def __init__(self, settings: WatchSettings) -> None:
        self.settings = settings
        self._stop_event = asyncio.Event()

    async def run(self) -> None:
        self.settings.directory.mkdir(parents=True, exist_ok=True)
        LOGGER.info(
            "RAG ingest watcher started. directory=%s interval=%ss",
            self.settings.directory,
            self.settings.interval_seconds,
        )

        while not self._stop_event.is_set():
            await asyncio.to_thread(self._ingest_ready_files)

            try:
                await asyncio.wait_for(
                    self._stop_event.wait(),
                    timeout=self.settings.interval_seconds,
                )
            except TimeoutError:
                pass

        LOGGER.info("RAG ingest watcher stopped.")

    def stop(self) -> None:
        self._stop_event.set()

    def _ingest_ready_files(self) -> None:
        indexed_count = 0

        for file_path in self._iter_ready_files():
            if not self._claim_file(file_path):
                continue
            try:
                self._ingest_file(file_path)
                self._delete_indexed_file(file_path)
                indexed_count += 1
            except Exception:
                LOGGER.exception("Failed to index watched file. path=%s", file_path)
            finally:
                self._release_file_claim(file_path)

        if indexed_count > 0:
            self._remove_empty_directories()

    def _claim_file(self, file_path: Path) -> bool:
        """Atomically claim a file so two watcher processes cannot upload it."""
        lock_path = file_path.with_name(f".{file_path.name}{PROCESSING_LOCK_SUFFIX}")
        try:
            with lock_path.open("x", encoding="ascii") as lock_file:
                lock_file.write(str(os.getpid()))
            return True
        except FileExistsError:
            try:
                if time.time() - lock_path.stat().st_mtime > PROCESSING_LOCK_MAX_AGE_SECONDS:
                    lock_path.unlink()
                    return self._claim_file(file_path)
            except FileNotFoundError:
                return self._claim_file(file_path)
            return False
    def _release_file_claim(self, file_path: Path) -> None:
        lock_path = file_path.with_name(f".{file_path.name}{PROCESSING_LOCK_SUFFIX}")
        try:
            lock_path.unlink()
        except FileNotFoundError:
            pass
    def _delete_indexed_file(self, file_path: Path) -> None:
        try:
            file_path.unlink()
            LOGGER.info("Indexed and deleted watched file. path=%s", file_path)
        except FileNotFoundError:
            LOGGER.info("Watched file was already removed after indexing. path=%s", file_path)
    def _iter_ready_files(self) -> list[Path]:
        return [
            file_path
            for file_path in sorted(self.settings.directory.rglob("*"))
            if self._is_ready_file(file_path)
        ]

    def _is_ready_file(self, file_path: Path) -> bool:
        if not file_path.is_file():
            return False
        if file_path.suffix.lower() not in SUPPORTED_EXTENSIONS:
            return False
        if file_path.name.endswith((".tmp", ".part", ".crdownload")):
            return False

        age_seconds = time.time() - file_path.stat().st_mtime
        return age_seconds >= self.settings.min_file_age_seconds

    def _remove_empty_directories(self) -> None:
        directories = [
            path
            for path in self.settings.directory.rglob("*")
            if path.is_dir()
        ]

        for directory in sorted(directories, key=lambda path: len(path.parts), reverse=True):
            try:
                directory.rmdir()
                LOGGER.info("Removed empty watched directory. path=%s", directory)
            except OSError:
                pass

    def _ingest_file(self, file_path: Path) -> None:
        document_type = "STANDARD_SOURCE" if file_path.suffix.lower() == ".java" else "STANDARD_DOCUMENT"

        # Inbox ingestion is now an adapter into Spring DocumentService.
        # DocumentIndexWorkflow owns the duplicated work that used to live here:
        #
        # - VectorDB source deletion and chunk indexing:
        #   content = file_path.read_text(encoding="utf-8", errors="ignore")
        #   self.vector_store.add_document(
        #       source=source,
        #       content=content,
        #       chunk_size=self.settings.chunk_size,
        #       overlap=self.settings.overlap,
        #   )
        #
        # - Java SourceGraph indexing:
        #   if file_path.suffix.lower() == ".java":
        #       self._ingest_java_source_graph(source=source, file_name=file_path.name, content=content)
        #
        # Keeping those steps disabled here makes every inbox file pass through
        # the same Spring DocumentService -> DocumentIndexWorkflow pipeline as
        # files uploaded from the Documents UI.
        response = self._upload_document(file_path=file_path, document_type=document_type)
        status = str(response.get("indexStatus") or "").upper()
        if status != "INDEXED":
            error_message = response.get("errorMessage") or response
            raise RuntimeError(f"Spring document indexing failed. status={status} error={error_message}")

        LOGGER.info(
            "Indexed watched file through Spring Documents API. path=%s document_id=%s document_type=%s",
            file_path,
            response.get("id"),
            response.get("documentType"),
        )

    def _upload_document(self, file_path: Path, document_type: str) -> dict[str, Any]:
        return self._post_multipart(
            self.settings.document_upload_url,
            fields={"documentType": document_type},
            file_path=file_path,
        )

    def _post_multipart(self, url: str, fields: dict[str, str], file_path: Path) -> dict[str, Any]:
        boundary = f"----AiAgentBoundary{uuid.uuid4().hex}"
        body = bytearray()

        def append_line(value: str = "") -> None:
            body.extend(value.encode("utf-8"))
            body.extend(b"\r\n")

        for name, value in fields.items():
            append_line(f"--{boundary}")
            append_line(f'Content-Disposition: form-data; name="{name}"')
            append_line()
            append_line(value)

        content_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
        append_line(f"--{boundary}")
        append_line(f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"')
        append_line(f"Content-Type: {content_type}")
        append_line()
        body.extend(file_path.read_bytes())
        body.extend(b"\r\n")
        append_line(f"--{boundary}--")

        request = urllib.request.Request(
            url=url,
            data=bytes(body),
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=DOCUMENT_UPLOAD_TIMEOUT_SECONDS) as response:
                response_body = response.read().decode("utf-8")
        except urllib.error.HTTPError as exception:
            response_body = exception.read().decode("utf-8", errors="ignore")
            raise RuntimeError(f"Spring document API returned HTTP {exception.code}: {response_body}") from exception
        except urllib.error.URLError as exception:
            raise RuntimeError(f"Spring document API is unreachable: {exception.reason}") from exception

        if not response_body.strip():
            return {}
        loaded = json.loads(response_body)
        return loaded if isinstance(loaded, dict) else {}
