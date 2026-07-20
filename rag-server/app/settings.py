from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - local fallback when PyYAML is not installed
    yaml = None


BASE_DIR = Path(__file__).resolve().parent.parent
APPLICATION_YML = BASE_DIR.parent / "src" / "main" / "resources" / "application.yml"


@dataclass(frozen=True)
class WatchSettings:
    enabled: bool
    directory: Path
    interval_seconds: int
    source: str
    document_upload_url: str
    source_graph_url: str
    chunk_size: int
    overlap: int
    min_file_age_seconds: int


def load_watch_settings() -> WatchSettings:
    config = _load_application_yml()
    watch_config = _get_nested(config, ["rag", "watch"], default={})

    return WatchSettings(
        enabled=_get_bool(
            "RAG_WATCH_ENABLED",
            default=_get_config_bool(watch_config, "enabled", True),
        ),
        directory=_resolve_directory(
            _get_value("RAG_WATCH_DIR", watch_config, "directory", BASE_DIR / "inbox")
        ),
        interval_seconds=_get_int(
            "RAG_WATCH_INTERVAL_SECONDS",
            default=_get_config_int(watch_config, "interval-seconds", 30),
            minimum=1,
        ),
        source=str(_get_value("RAG_WATCH_SOURCE", watch_config, "source", "watched-source")),
        document_upload_url=str(_get_value(
            "RAG_WATCH_DOCUMENT_UPLOAD_URL",
            watch_config,
            "document-upload-url",
            "http://localhost:8081/api/documents",
        )),
        source_graph_url=str(_get_value(
            "RAG_WATCH_SOURCE_GRAPH_URL",
            watch_config,
            "source-graph-url",
            "http://localhost:8081/api/source-graph/java-files",
        )),
        chunk_size=_get_int(
            "RAG_CHUNK_SIZE",
            default=_get_config_int(watch_config, "chunk-size", 1200),
            minimum=200,
        ),
        overlap=_get_int(
            "RAG_CHUNK_OVERLAP",
            default=_get_config_int(watch_config, "overlap", 150),
            minimum=0,
        ),
        min_file_age_seconds=_get_int(
            "RAG_WATCH_MIN_FILE_AGE_SECONDS",
            default=_get_config_int(watch_config, "min-file-age-seconds", 2),
            minimum=0,
        ),
    )


def _get_bool(name: str, default: bool) -> bool:
    value = _resolve_env_placeholder(os.getenv(name))
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def _get_int(name: str, default: int, minimum: int) -> int:
    value = _resolve_env_placeholder(os.getenv(name))
    if value is None:
        return default

    try:
        parsed = int(value)
    except ValueError:
        return default

    return max(minimum, parsed)


def _get_value(name: str, config: dict[str, Any], key: str, default: Any) -> Any:
    env_value = _resolve_env_placeholder(os.getenv(name))
    if env_value is not None:
        return env_value

    config_value = _resolve_env_placeholder(config.get(key))
    if config_value is not None:
        return config_value

    return default


def _get_config_bool(config: dict[str, Any], key: str, default: bool) -> bool:
    value = _resolve_env_placeholder(config.get(key))
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def _get_config_int(config: dict[str, Any], key: str, default: int) -> int:
    value = _resolve_env_placeholder(config.get(key))
    if value is None:
        return default
    try:
        return int(value)
    except ValueError:
        return default


def _load_application_yml() -> dict[str, Any]:
    if not APPLICATION_YML.exists() or yaml is None:
        return {}

    with APPLICATION_YML.open(encoding="utf-8") as file:
        loaded = yaml.safe_load(file) or {}

    return loaded if isinstance(loaded, dict) else {}


def _get_nested(config: dict[str, Any], keys: list[str], default: Any) -> Any:
    current: Any = config
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return default
        current = current[key]
    return current if isinstance(current, dict) else default


def _resolve_env_placeholder(value: Any) -> Any:
    if not isinstance(value, str):
        return value

    stripped = value.strip()
    if not stripped.startswith("${") or not stripped.endswith("}"):
        return value

    expression = stripped[2:-1]
    env_name, separator, fallback = expression.partition(":")
    env_value = os.getenv(env_name)
    if env_value is not None:
        return env_value
    return fallback if separator else None


def _resolve_directory(value: Any) -> Path:
    path = Path(str(value))
    if path.is_absolute():
        return path.resolve()
    return (BASE_DIR.parent / path).resolve()
