from __future__ import annotations

import asyncio
import json
import logging
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from app.settings import WatchSettings
from app.vector_store import LocalVectorStore


LOGGER = logging.getLogger("rag.directory_watcher")

SUPPORTED_EXTENSIONS = {
    ".java",
    ".kt",
    ".xml",
    ".yml",
    ".yaml",
    ".md",
    ".js",
    ".jsx",
    ".ts",
    ".tsx",
}

SOURCE_GRAPH_SUCCESS_STATUSES = {"SUCCESS", "SKIPPED"}
SOURCE_GRAPH_TIMEOUT_SECONDS = 20


class DirectoryIngestWatcher:
    def __init__(self, settings: WatchSettings, vector_store: LocalVectorStore) -> None:
        self.settings = settings
        self.vector_store = vector_store
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
            try:
                self._ingest_file(file_path)
                file_path.unlink()
                indexed_count += 1
                LOGGER.info("Indexed and deleted watched file. path=%s", file_path)
            except Exception:
                LOGGER.exception("Failed to index watched file. path=%s", file_path)

        if indexed_count > 0:
            self._remove_empty_directories()

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
        content = file_path.read_text(encoding="utf-8", errors="ignore")
        relative_path = file_path.relative_to(self.settings.directory)
        source = f"{self.settings.source}:{relative_path.as_posix()}"

        if file_path.suffix.lower() == ".java":
            self._ingest_java_source_graph(
                source=source,
                file_name=relative_path.name,
                content=content,
            )
            return

        stored_count = self.vector_store.add_document(
            source=source,
            content=content,
            chunk_size=self.settings.chunk_size,
            overlap=self.settings.overlap,
        )
        LOGGER.info("Indexed watched file into Vector DB. path=%s stored_count=%s", file_path, stored_count)

    def _ingest_java_source_graph(self, source: str, file_name: str, content: str) -> None:
        if not self.settings.source_graph_url.strip():
            raise RuntimeError("RAG watch source graph URL is required for Java files.")

        response = self._post_json(
            self.settings.source_graph_url,
            {
                "source": source,
                "fileName": file_name,
                "content": content,
            },
        )
        status = str(response.get("status") or "").upper()
        if status not in SOURCE_GRAPH_SUCCESS_STATUSES:
            error_message = response.get("errorMessage") or response
            raise RuntimeError(f"Spring Boot source graph indexing failed. status={status} error={error_message}")

        LOGGER.info("Indexed watched Java file into Neo4j. source=%s status=%s", source, status)

    def _post_json(self, url: str, payload: dict[str, Any]) -> dict[str, Any]:
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            url=url,
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=SOURCE_GRAPH_TIMEOUT_SECONDS) as response:
                body = response.read().decode("utf-8")
        except urllib.error.HTTPError as exception:
            body = exception.read().decode("utf-8", errors="ignore")
            raise RuntimeError(f"Spring Boot source graph API returned HTTP {exception.code}: {body}") from exception
        except urllib.error.URLError as exception:
            raise RuntimeError(f"Spring Boot source graph API is unreachable: {exception.reason}") from exception

        if not body.strip():
            return {}
        loaded = json.loads(body)
        return loaded if isinstance(loaded, dict) else {}