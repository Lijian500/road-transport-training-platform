[CmdletBinding()]
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $projectRoot "backend"
$frontendDirectory = Join-Path $projectRoot "frontend"

if (-not $SkipBackend) {
    $backendPom = Join-Path $backendDirectory "pom.xml"
    if (Test-Path -LiteralPath $backendPom) {
        Push-Location $backendDirectory
        try {
            $mavenWrapper = Join-Path $backendDirectory "mvnw.cmd"
            if (Test-Path -LiteralPath $mavenWrapper) {
                & $mavenWrapper clean verify
            }
            else {
                mvn clean verify
            }
        }
        finally {
            Pop-Location
        }
    }
    else {
        Write-Warning "尚未找到 backend/pom.xml，跳过后端构建。"
    }
}

if (-not $SkipFrontend) {
    $frontendPackage = Join-Path $frontendDirectory "package.json"
    if (Test-Path -LiteralPath $frontendPackage) {
        Push-Location $frontendDirectory
        try {
            $lockFile = Join-Path $frontendDirectory "pnpm-lock.yaml"
            if (Test-Path -LiteralPath $lockFile) {
                pnpm install --frozen-lockfile
            }
            else {
                Write-Warning "尚无pnpm-lock.yaml，本次执行普通安装；依赖安装成功后应提交生成的锁文件。"
                pnpm install --no-frozen-lockfile
            }
            pnpm build
        }
        finally {
            Pop-Location
        }
    }
    else {
        Write-Warning "尚未找到 frontend/package.json，跳过前端构建。"
    }
}

Write-Host "项目构建流程执行完成。"
