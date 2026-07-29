[CmdletBinding()]
param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$deployDirectory = Join-Path $projectRoot "deploy"
$environmentFile = Join-Path $projectRoot ".env"

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw "未找到根目录 .env，无法确认当前基础设施配置。"
}

Push-Location $deployDirectory
try {
    if ($RemoveVolumes) {
        $confirmation = Read-Host "该操作会删除本项目的MySQL、Redis、RabbitMQ和Nacos本地数据卷。输入 DELETE 确认"
        if ($confirmation -cne "DELETE") {
            Write-Host "已取消删除。"
            exit 0
        }
        docker compose --env-file $environmentFile down --volumes
    }
    else {
        docker compose --env-file $environmentFile down
    }
}
finally {
    Pop-Location
}
