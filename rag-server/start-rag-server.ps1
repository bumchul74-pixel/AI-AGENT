param(
    [string]$HostAddress = "0.0.0.0",
    [int]$Port = 8000,
    [switch]$Restart,
    [switch]$NoReload,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

if ($Restart) {
    Write-Host "Stopping RAG server processes using port $Port..."

    $Connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    $ProcessIds = $Connections |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -and $_ -ne $PID }

    if ($ProcessIds) {
        foreach ($ProcessId in $ProcessIds) {
            $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
            if ($Process) {
                Write-Host "Stopping process $($Process.ProcessName) ($ProcessId)"
                Stop-Process -Id $ProcessId -Force
            }
        }

        Start-Sleep -Seconds 2
    } else {
        Write-Host "No running RAG server process found on port $Port."
    }
}

$VenvPython = Join-Path $ScriptDir ".venv\Scripts\python.exe"

if (-not (Test-Path $VenvPython)) {
    Write-Host "Creating Python virtual environment..."
    python -m venv .venv
}

if (-not $SkipInstall) {
    Write-Host "Installing Python dependencies..."
    & $VenvPython -m pip install -r requirements.txt
}

$InboxDir = Join-Path $ScriptDir "inbox"
if (-not (Test-Path $InboxDir)) {
    New-Item -ItemType Directory -Path $InboxDir | Out-Null
}

$UvicornArgs = @(
    "app.main:app",
    "--host", $HostAddress,
    "--port", $Port
)

if (-not $NoReload) {
    $UvicornArgs += "--reload"
}

if ($Restart) {
    Write-Host "Restarting RAG server at http://localhost:$Port"
} else {
    Write-Host "Starting RAG server at http://localhost:$Port"
}
Write-Host "Watch directory: $InboxDir"

& $VenvPython -m uvicorn @UvicornArgs
