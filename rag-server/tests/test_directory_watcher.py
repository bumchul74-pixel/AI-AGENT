from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from typing import Any

from app.directory_watcher import DirectoryIngestWatcher
from app.settings import WatchSettings


class RecordingDirectoryIngestWatcher(DirectoryIngestWatcher):
    def __init__(
            self,
            settings: WatchSettings,
            response: dict[str, Any],
            delete_after_upload: bool = False,
    ) -> None:
        super().__init__(settings)
        self.response = response
        self.delete_after_upload = delete_after_upload
        self.uploads: list[dict[str, Any]] = []

    def _post_multipart(self, url: str, fields: dict[str, str], file_path: Path) -> dict[str, Any]:
        content = file_path.read_text(encoding="utf-8")
        self.uploads.append({
            "url": url,
            "fields": dict(fields),
            "file_name": file_path.name,
            "content": content,
        })
        if self.delete_after_upload:
            file_path.unlink()
        return self.response


class DirectoryIngestWatcherTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.inbox = Path(self.temp_dir.name) / "inbox"
        self.inbox.mkdir()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_ready_file_is_uploaded_to_spring_documents_api_and_deleted_on_success(self) -> None:
        file_path = self.inbox / "standard" / "api-standard.md"
        file_path.parent.mkdir()
        file_path.write_text("# API Standard", encoding="utf-8")
        watcher = RecordingDirectoryIngestWatcher(
            self.settings(),
            response={"id": 10, "indexStatus": "INDEXED", "documentType": "STANDARD_DOCUMENT"},
        )

        watcher._ingest_ready_files()

        self.assertEqual(1, len(watcher.uploads))
        self.assertEqual("http://spring.test/api/documents", watcher.uploads[0]["url"])
        self.assertEqual({"documentType": "STANDARD_DOCUMENT"}, watcher.uploads[0]["fields"])
        self.assertEqual("api-standard.md", watcher.uploads[0]["file_name"])
        self.assertEqual("# API Standard", watcher.uploads[0]["content"])
        self.assertFalse(file_path.exists())
        self.assertFalse(file_path.parent.exists())

    def test_java_file_is_uploaded_as_standard_source(self) -> None:
        file_path = self.inbox / "UserController.java"
        file_path.write_text("public class UserController {}", encoding="utf-8")
        watcher = RecordingDirectoryIngestWatcher(
            self.settings(),
            response={"id": 11, "indexStatus": "INDEXED", "documentType": "STANDARD_SOURCE"},
        )

        watcher._ingest_ready_files()

        self.assertEqual(1, len(watcher.uploads))
        self.assertEqual({"documentType": "STANDARD_SOURCE"}, watcher.uploads[0]["fields"])
        self.assertFalse(file_path.exists())

    def test_already_removed_file_after_success_does_not_fail(self) -> None:
        file_path = self.inbox / "api-standard.md"
        file_path.write_text("# API Standard", encoding="utf-8")
        watcher = RecordingDirectoryIngestWatcher(
            self.settings(),
            response={"id": 13, "indexStatus": "INDEXED", "documentType": "STANDARD_DOCUMENT"},
            delete_after_upload=True,
        )

        with self.assertLogs("rag.directory_watcher", level="INFO") as logs:
            watcher._ingest_ready_files()

        self.assertEqual(1, len(watcher.uploads))
        self.assertFalse(file_path.exists())
        self.assertIn("already removed", "\n".join(logs.output))
    def test_failed_document_response_keeps_file_for_next_scan(self) -> None:
        file_path = self.inbox / "api-standard.md"
        file_path.write_text("# API Standard", encoding="utf-8")
        watcher = RecordingDirectoryIngestWatcher(
            self.settings(),
            response={"id": 12, "indexStatus": "FAILED", "errorMessage": "VectorDB unavailable"},
        )

        with self.assertLogs("rag.directory_watcher", level="ERROR") as logs:
            watcher._ingest_ready_files()

        self.assertEqual(1, len(watcher.uploads))
        self.assertTrue(file_path.exists())
        self.assertIn("Failed to index watched file", "\n".join(logs.output))

    def settings(self) -> WatchSettings:
        return WatchSettings(
            enabled=True,
            directory=self.inbox,
            interval_seconds=30,
            source="backend-source",
            document_upload_url="http://spring.test/api/documents",
            source_graph_url="http://spring.test/api/source-graph/java-files",
            chunk_size=1200,
            overlap=150,
            min_file_age_seconds=0,
        )


if __name__ == "__main__":
    unittest.main()
