from __future__ import annotations

import tempfile
import unittest
import json
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

    def test_vector_chunks_store_stable_identity_metadata(self) -> None:
        result = rag_ingest_document(
            self.store,
            source="document:10",
            content="public class UserService {}",
            chunk_size=200,
            overlap=0,
            project_id="commerce",
            file_path="src/main/java/com/example/UserService.java",
            file_hash="abc123",
            entity_ids=["file:commerce:src/main/java/com/example/UserService.java"],
            document_id=10,
            symbol="UserService",
            metadata={"moduleName": "backend"},
        )

        self.assertEqual({"stored_count": 1}, result)
        chunk = self.store.chunks[0]
        self.assertEqual("document:10:chunk:0", chunk.chunk_id)
        self.assertEqual(10, chunk.document_id)
        self.assertEqual("document:10", chunk.source_key)
        self.assertEqual("abc123", chunk.file_hash)
        self.assertEqual("commerce", chunk.project_id)
        self.assertEqual("UserService", chunk.symbol)
        self.assertEqual({"moduleName": "backend"}, chunk.metadata)
        self.assertEqual(
            ["file:commerce:src/main/java/com/example/UserService.java"],
            chunk.entity_ids,
        )
        payload = json.loads(self.store.store_path.read_text(encoding="utf-8"))[0]
        self.assertEqual("document:10:chunk:0", payload["chunkId"])
        self.assertEqual(10, payload["documentId"])
        self.assertEqual("document:10", payload["sourceKey"])
        self.assertIn("embedding", payload)
        self.assertNotIn("source", payload)
        self.assertNotIn("vector", payload)

    def test_legacy_vector_chunks_load_with_empty_metadata(self) -> None:
        store_path = Path(self.temp_dir.name) / "legacy-vector-store.json"
        store_path.write_text(
            '[{"source":"legacy.java","content":"class Legacy {}",'
            '"vector":[1.0,0.0]}]',
            encoding="utf-8",
        )

        legacy_store = LocalVectorStore(store_path)

        self.assertEqual("", legacy_store.chunks[0].chunk_id)
        self.assertEqual([], legacy_store.chunks[0].entity_ids)
        self.assertEqual("legacy.java", legacy_store.chunks[0].source_key)
        self.assertEqual([1.0, 0.0], legacy_store.chunks[0].embedding)

    def test_structured_search_and_chunk_batch_preserve_entity_ids(self) -> None:
        rag_ingest_document(
            self.store,
            source="document:20",
            content="public class UserController {}",
            chunk_size=200,
            overlap=0,
            entity_ids=["type:commerce:com.example.UserController"],
        )

        results = self.store.search_chunks("UserController", 5)
        reloaded = self.store.find_chunks(["document:20:chunk:0"])

        self.assertEqual("document:20:chunk:0", results[0]["chunkId"])
        self.assertGreater(results[0]["score"], 0)
        self.assertEqual(
            ["type:commerce:com.example.UserController"],
            results[0]["entityIds"],
        )
        self.assertEqual(results[0]["content"], reloaded[0]["content"])

    def test_search_chunks_are_filtered_by_project_id(self) -> None:
        rag_ingest_document(
            self.store, source="document:project-a", content="Shared user service project alpha",
            chunk_size=200, overlap=0, project_id="project-a",
        )
        rag_ingest_document(
            self.store, source="document:project-b", content="Shared user service project beta",
            chunk_size=200, overlap=0, project_id="project-b",
        )

        results = self.store.search_chunks("Shared user service", 10, project_id="project-b")

        self.assertEqual(1, len(results))
        self.assertEqual("document:project-b", results[0]["sourceKey"])

    def test_explicit_statement_chunks_are_replaced_by_stable_chunk_id(self) -> None:
        statement_chunk = {
            "chunkId": "document:30:statement:findUser",
            "sourceKey": "document:30",
            "content": "SELECT id, name FROM users WHERE id = #{id}",
            "documentId": 30,
            "projectId": "commerce",
            "filePath": "mapper/UserMapper.xml",
            "fileHash": "hash30",
            "entityIds": ["statement:commerce:com.example.UserMapper:findUser"],
            "symbol": "com.example.UserMapper.findUser",
            "metadata": {"contentType": "mybatis-statement", "dynamic": False},
        }

        self.assertEqual(1, self.store.add_chunks([statement_chunk]))
        statement_chunk["content"] = "SELECT id, name, email FROM users WHERE id = #{id}"
        self.assertEqual(1, self.store.add_chunks([statement_chunk]))

        self.assertEqual(1, len(self.store.chunks))
        chunk = self.store.chunks[0]
        self.assertIn("email", chunk.content)
        self.assertEqual(
            ["statement:commerce:com.example.UserMapper:findUser"],
            chunk.entity_ids,
        )
        self.assertEqual("mybatis-statement", chunk.metadata["contentType"])


if __name__ == "__main__":
    unittest.main()
