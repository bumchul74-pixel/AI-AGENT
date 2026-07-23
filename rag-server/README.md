# Local Python RAG Server

Spring Boot backend expects this server at `http://localhost:8000`.

## Run

```powershell
cd C:\workspace\AI-AGENT\rag-server
.\start-rag-server.ps1
```

The script creates `.venv` if needed, installs dependencies, creates the
`inbox` directory, and starts FastAPI on port `8000`.

Optional arguments:

```powershell
.\start-rag-server.ps1 -Port 8000
.\start-rag-server.ps1 -HostAddress 127.0.0.1 -Port 8000
.\start-rag-server.ps1 -Restart
.\start-rag-server.ps1 -NoReload
.\start-rag-server.ps1 -SkipInstall
```

## Restart

```powershell
cd C:\workspace\AI-AGENT\rag-server
.\start-rag-server.ps1 -Restart
```

The `-Restart` option stops the process listening on port `8000`, then starts
the RAG server again.

Optional arguments:

```powershell
.\start-rag-server.ps1 -Restart -Port 8000
.\start-rag-server.ps1 -Restart -HostAddress 127.0.0.1 -Port 8000
.\start-rag-server.ps1 -Restart -NoReload
.\start-rag-server.ps1 -Restart -SkipInstall
```

Manual run:

```powershell
cd C:\workspace\AI-AGENT\rag-server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Index Documents

```powershell
python scripts/index_directory.py --path ..\src\main\java --source backend-source
```

## Watch And Index Files

The RAG server can poll a drop directory, index supported files, and delete each
file after successful indexing.

Default watch directory:

```text
rag-server\inbox
```

Run with defaults:

```powershell
cd rag-server
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Put Java files or folders containing Java files into `rag-server\inbox`.
The server checks the directory every 30 seconds, recursively indexes ready
files, and deletes each indexed file after successful indexing. Empty folders
are removed after their files are indexed.

Example:

```text
rag-server\inbox\standard-source\UserController.java
rag-server\inbox\standard-source\service\UserService.java
```

Do not put source folders directly under `rag-server`; put them under
`rag-server\inbox`.

Configuration in `src\main\resources\application.yml`:

```yaml
rag:
  watch:
    enabled: ${RAG_WATCH_ENABLED:true}
    directory: ${RAG_WATCH_DIR:rag-server/inbox}
    interval-seconds: ${RAG_WATCH_INTERVAL_SECONDS:30}
    source: ${RAG_WATCH_SOURCE:backend-source}
    chunk-size: ${RAG_CHUNK_SIZE:1200}
    overlap: ${RAG_CHUNK_OVERLAP:150}
    min-file-age-seconds: ${RAG_WATCH_MIN_FILE_AGE_SECONDS:2}
```

Environment variables override `application.yml`:

```powershell
$env:RAG_WATCH_ENABLED="true"
$env:RAG_WATCH_DIR="C:\workspace\AI-AGENT\rag-server\inbox"
$env:RAG_WATCH_INTERVAL_SECONDS="30"
$env:RAG_WATCH_SOURCE="backend-source"
$env:RAG_CHUNK_SIZE="1200"
$env:RAG_CHUNK_OVERLAP="150"
```

Do not point `RAG_WATCH_DIR` at `src\main\java` unless deleting those source
files after indexing is intentional.

## EasyOCR MCP Server

EasyOCR MCP runs as a separate process and shares the rag-server virtual
environment. Korean and English are enabled by default, and model files are
stored under data/easyocr-models.

Install OCR dependencies:

    ./.venv/Scripts/python.exe -m pip install -r requirements-ocr-mcp.txt

Run with the default stdio transport:

    ./.venv/Scripts/python.exe -m app.ocr.easyocr_server

Run with streamable HTTP on port 8001 using the start script:

    ./start-easyocr-server.ps1

Restart the server or skip dependency installation:

    ./start-easyocr-server.ps1 -Restart
    ./start-easyocr-server.ps1 -SkipInstall

Optional arguments:

    ./start-easyocr-server.ps1 -HostAddress 127.0.0.1 -Port 8001
    ./start-easyocr-server.ps1 -Languages "ko,en"
    ./start-easyocr-server.ps1 -Gpu
    ./start-easyocr-server.ps1 -ModelDirectory "D:/models/easyocr"
    ./start-easyocr-server.ps1 -AllowedDirectories "D:/images;D:/uploads"

Available tools:

- ocr_image_file: reads an image under rag-server/inbox or project uploads
- ocr_image_base64: reads Base64 image data or an image data URL

AI-MCP connects to this server at `http://localhost:8001/ocr` and re-exposes
`ocr_image_file` and `ocr_image_base64` through the AI-MCP `/mcp` endpoint.
Configure the downstream connection in the AI-MCP process with
`EASYOCR_MCP_URL` and `EASYOCR_MCP_ENDPOINT`.

Configuration environment variables:

    $env:EASYOCR_LANGUAGES="ko,en"
    $env:EASYOCR_GPU="false"
    $env:EASYOCR_MODEL_DIR="D:/workspace/AI-AGENT/rag-server/data/easyocr-models"
    $env:EASYOCR_ALLOWED_DIRS="D:/images;D:/workspace/AI-AGENT/uploads"

## Search API

```http
POST /api/search
Content-Type: application/json

{
  "query": "User controller structure"
}
```
