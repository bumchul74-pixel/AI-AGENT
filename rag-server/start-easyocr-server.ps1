param(
    [string]$HostAddress = "127.0.0.1",
    [ValidateRange(1, 65535)]
    [int]$Port = 8001,
    [string]$Languages = "ko,en",
    [string]$ModelDirectory = "",
    [string]$AllowedDirectories = "",
    [switch]$Gpu,
    [switch]$Restart,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

if ($Restart) {
    Write-Host "Stopping EasyOCR MCP server processes using port $Port..."

    $EasyOcrConnections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    $EasyOcrProcessIds = $EasyOcrConnections |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -and $_ -ne $PID }

    if ($EasyOcrProcessIds) {
        foreach ($EasyOcrProcessId in $EasyOcrProcessIds) {
            $EasyOcrProcess = Get-Process -Id $EasyOcrProcessId -ErrorAction SilentlyContinue
            if ($EasyOcrProcess) {
                Write-Host "Stopping process $($EasyOcrProcess.ProcessName) ($EasyOcrProcessId)"
                Stop-Process -Id $EasyOcrProcessId -Force
            }
        }

        Start-Sleep -Seconds 2
    } else {
        Write-Host "No running EasyOCR MCP server process found on port $Port."
    }
}

$VenvPython = Join-Path $ScriptDir ".venv/Scripts/python.exe"

if (-not (Test-Path $VenvPython)) {
    Write-Host "Creating Python virtual environment..."
    python -m venv .venv
}

if (-not $SkipInstall) {
    Write-Host "Installing EasyOCR MCP dependencies..."
    & $VenvPython -m pip install -r requirements-ocr-mcp.txt
}

$ResolvedModelDirectory = if ($ModelDirectory) {
    [System.IO.Path]::GetFullPath($ModelDirectory)
} else {
    Join-Path $ScriptDir "data/easyocr-models"
}
if (-not (Test-Path $ResolvedModelDirectory)) {
    New-Item -ItemType Directory -Path $ResolvedModelDirectory -Force | Out-Null
}

$InboxDirectory = Join-Path $ScriptDir "inbox"
if (-not (Test-Path $InboxDirectory)) {
    New-Item -ItemType Directory -Path $InboxDirectory -Force | Out-Null
}

$ProjectUploadsDirectory = Join-Path (Split-Path -Parent $ScriptDir) "uploads"
$ResolvedAllowedDirectories = if ($AllowedDirectories) {
    $AllowedDirectories
} else {
    $InboxDirectory + [System.IO.Path]::PathSeparator + $ProjectUploadsDirectory
}

$env:EASYOCR_MCP_TRANSPORT = "streamable-http"
$env:EASYOCR_MCP_HOST = $HostAddress
$env:EASYOCR_MCP_PORT = [string]$Port
$env:EASYOCR_LANGUAGES = $Languages
$env:EASYOCR_MODEL_DIR = $ResolvedModelDirectory
$env:EASYOCR_ALLOWED_DIRS = $ResolvedAllowedDirectories
if ($Gpu) {
    $env:EASYOCR_GPU = "true"
} else {
    $env:EASYOCR_GPU = "false"
}

if ($Restart) {
    Write-Host ("Restarting EasyOCR MCP server at http://{0}:{1}/ocr" -f $HostAddress, $Port)
} else {
    Write-Host ("Starting EasyOCR MCP server at http://{0}:{1}/ocr" -f $HostAddress, $Port)
}
Write-Host "Languages: $Languages"
Write-Host "GPU enabled: $($Gpu.IsPresent)"
Write-Host "Model directory: $ResolvedModelDirectory"
Write-Host "Allowed directories: $ResolvedAllowedDirectories"

& $VenvPython -m app.ocr.easyocr_server
