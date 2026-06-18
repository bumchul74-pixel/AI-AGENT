param(
    [string]$HostAddress,
    [int]$Port,
    [switch]$Restart,
    [switch]$NoReload,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RagServerScript = Join-Path $ProjectRoot "rag-server\start-rag-server.ps1"

if (-not (Test-Path $RagServerScript)) {
    throw "RAG server script was not found: $RagServerScript"
}

& $RagServerScript @PSBoundParameters
