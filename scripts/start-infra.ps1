[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$deployDirectory = Join-Path $projectRoot "deploy"
$environmentFile = Join-Path $projectRoot ".env"
$environmentExample = Join-Path $projectRoot ".env.example"

if (-not (Test-Path -LiteralPath $environmentFile)) {
    Copy-Item -LiteralPath $environmentExample -Destination $environmentFile
    Write-Warning "已创建根目录 .env。请先替换其中的示例密码，再重新执行本脚本。"
    exit 1
}

$environmentContent = Get-Content -LiteralPath $environmentFile -Raw
if ($environmentContent -match "change[-_]me|replace-with") {
    throw "根目录 .env 仍包含示例密码，请替换后再启动。"
}

Push-Location $deployDirectory
try {
    docker compose --env-file $environmentFile config --quiet
    docker compose --env-file $environmentFile up -d
    docker compose --env-file $environmentFile ps
}
finally {
    Pop-Location
}
