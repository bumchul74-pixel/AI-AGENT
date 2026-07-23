from __future__ import annotations

import base64
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.ocr.easyocr_server import (
    _decode_base64_image,
    _normalize_results,
    _resolve_image_path,
)


class EasyOcrMcpTest(unittest.TestCase):
    def test_normalizes_easyocr_regions(self) -> None:
        result = _normalize_results([
            ([[0, 0], [10, 0], [10, 5], [0, 5]], "안녕하세요", 0.97),
            ([[0, 6], [10, 6], [10, 11], [0, 11]], "EasyOCR", 0.91),
        ])

        self.assertEqual(chr(10).join(["안녕하세요", "EasyOCR"]), result["text"])
        self.assertEqual(2, result["region_count"])
        self.assertEqual(0.97, result["regions"][0]["confidence"])
        self.assertEqual(["ko", "en"], result["languages"])

    def test_decodes_image_data_url(self) -> None:
        raw_image = b"image-bytes"
        encoded = base64.b64encode(raw_image).decode("ascii")

        self.assertEqual(
            raw_image,
            _decode_base64_image(f"data:image/png;base64,{encoded}"),
        )

    def test_file_access_is_restricted_to_configured_directories(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            allowed_directory = Path(directory).resolve()
            image = allowed_directory / "sample.png"
            image.write_bytes(b"png")

            with patch.dict(os.environ, {"EASYOCR_ALLOWED_DIRS": str(allowed_directory)}):
                self.assertEqual(image, _resolve_image_path(str(image)))

            outside = allowed_directory.parent / "outside.png"
            with patch.dict(os.environ, {"EASYOCR_ALLOWED_DIRS": str(allowed_directory)}):
                with self.assertRaisesRegex(ValueError, "outside"):
                    _resolve_image_path(str(outside))


if __name__ == "__main__":
    unittest.main()
