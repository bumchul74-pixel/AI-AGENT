import argparse
from pathlib import Path
import sys

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.vector_store import LocalVectorStore


DEFAULT_EXTENSIONS = {
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--path", required=True)
    parser.add_argument("--source", default="local")
    parser.add_argument("--chunk-size", type=int, default=1200)
    parser.add_argument("--overlap", type=int, default=150)
    args = parser.parse_args()

    root = Path(args.path).resolve()
    store = LocalVectorStore()
    total = 0

    for file_path in root.rglob("*"):
        if not file_path.is_file() or file_path.suffix.lower() not in DEFAULT_EXTENSIONS:
            continue

        content = file_path.read_text(encoding="utf-8", errors="ignore")
        source = f"{args.source}:{file_path.relative_to(root)}"
        total += store.add_document(
            source=source,
            content=content,
            chunk_size=args.chunk_size,
            overlap=args.overlap,
        )

    print(f"indexed_chunks={total}")


if __name__ == "__main__":
    main()
