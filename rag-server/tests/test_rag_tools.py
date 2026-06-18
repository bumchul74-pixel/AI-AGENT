from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from app.rag_tools import rag_ingest_document, rag_search, rag_stats
from app.vector_store import LocalVectorStore


class RagToolsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.store = LocalVectorStore(Path(self.temp_dir.name) / "vector_store.json")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_ingest_search_and_stats_use_same_vector_store(self) -> None:
        ingest_result = rag_ingest_document(
            self.store,
            source="standard-source/UserController.java",
            content="@RestController public class UserController {}",
            chunk_size=200,
            overlap=0,
        )

        search_result = rag_search(self.store, query="UserController", top_k=5)
        stats_result = rag_stats(self.store)

        self.assertEqual({"stored_count": 1}, ingest_result)
        self.assertEqual(1, len(search_result["documents"]))
        self.assertIn("UserController", search_result["documents"][0])
        self.assertEqual({"java_file_count": 1}, stats_result)

    def test_search_returns_empty_documents_for_blank_query(self) -> None:
        result = rag_search(self.store, query="   ", top_k=5)

        self.assertEqual({"documents": []}, result)


if __name__ == "__main__":
    unittest.main()
