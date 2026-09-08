<#
.SYNOPSIS
仅停止 start-apps.ps1 记录的本仓库Java进程。
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$runDirectory = Join-Path $projectRoot 'tmp/local-run/current'
foreach ($file in Get-ChildItem -LiteralPath $runDirectory -Filter '*.json' -ErrorAction SilentlyContinue) {
    $state = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8 | ConvertFrom-Json
    $jarPath = [IO.Path]::GetFullPath($state.jarPath)
    if (-not $jarPath.StartsWith(($projectRoot + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) {
        throw '进程记录的JAR路径不属于当前仓库。'
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.processId)" -ErrorAction SilentlyContinue
    if ($process -and $process.Name -eq 'java.exe' -and $process.CommandLine.Contains($jarPath)) {
        Stop-Process -Id $state.processId
        Write-Output "$($file.BaseName)：已停止。"
    }
}
