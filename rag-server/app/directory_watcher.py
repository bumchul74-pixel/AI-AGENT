from __future__ import annotations

import asyncio
import logging
import time
from pathlib import Path

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
        stored_count = self.vector_store.add_document(
            source=source,
            content=content,
            chunk_size=self.settings.chunk_size,
            overlap=self.settings.overlap,
        )
        LOGGER.info("Indexed watched file. path=%s stored_count=%s", file_path, stored_count)
