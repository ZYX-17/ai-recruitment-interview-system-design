<#
.SYNOPSIS
  停止 scripts/dev/start-local.ps1 启动的服务（按 logs/local/pids.txt）。
#>
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$PidFile = Join-Path $Root "logs\local\pids.txt"
if (-not (Test-Path $PidFile)) {
    Write-Host "没有找到 $PidFile，可能尚未启动"
    return
}
Get-Content $PidFile | ForEach-Object {
    $Parts = $_ -split "=", 2
    if ($Parts.Count -ne 2) { return }
    $Name = $Parts[0]
    $ProcId = 0
    if (-not [int]::TryParse($Parts[1], [ref]$ProcId)) { return }
    $Proc = Get-Process -Id $ProcId -ErrorAction SilentlyContinue
    if ($Proc) {
        Stop-Process -Id $ProcId -Force
        Write-Host "已停止 $Name (PID $ProcId)"
    }
}
