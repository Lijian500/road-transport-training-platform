<#
.SYNOPSIS
使用已构建的JAR启动本地业务服务，环境文件只导入当前进程。
#>
[CmdletBinding()]
param(
    [string]$EnvironmentFile = '.env.local',
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet('train-admin-service', 'train-training-service', 'train-learning-service', 'train-web-api', 'train-realtime-service', 'train-gateway')]
    [string[]]$Service
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw '请使用 -JavaHome 指定JDK 17目录。' }
$javaPath = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaPath)) { throw '请使用 -JavaHome 指定JDK 17目录。' }
$javaVersion = & $javaPath -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "17\.') { throw '本项目启动脚本要求JDK 17。' }
$configPath = if ([IO.Path]::IsPathRooted($EnvironmentFile)) { $EnvironmentFile } else { Join-Path $projectRoot $EnvironmentFile }
if (-not (Test-Path -LiteralPath $configPath)) { throw '环境文件不存在，请先按 .env.example 配置。' }
foreach ($line in Get-Content -LiteralPath $configPath -Encoding utf8) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
}
$runDirectory = Join-Path $projectRoot 'tmp/local-run/current'
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$services = [ordered]@{
    'train-admin-service' = 8091
    'train-training-service' = 8092
    'train-learning-service' = 8093
    'train-web-api' = 8081
    'train-realtime-service' = 8082
    'train-gateway' = 8080
}
foreach ($entry in $services.GetEnumerator()) {
    if ($Service -and $entry.Key -notin $Service) { continue }
    $jarPath = Join-Path $projectRoot "backend/$($entry.Key)/target/$($entry.Key)-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $jarPath)) { throw "缺少构建产物：$($entry.Key)，请先执行 scripts/build.ps1。" }
}
foreach ($entry in $services.GetEnumerator()) {
    if ($Service -and $entry.Key -notin $Service) { continue }
    $statePath = Join-Path $runDirectory "$($entry.Key).json"
    if (Test-Path -LiteralPath $statePath) {
        $previousState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $previousProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($previousState.processId)" -ErrorAction SilentlyContinue
        if ($previousProcess -and $previousProcess.Name -eq 'java.exe' -and $previousProcess.CommandLine.Contains($previousState.jarPath)) {
            Write-Output "$($entry.Key)：已有本脚本启动的进程，PID=$($previousState.processId)。"
            continue
        }
    }
    if (Get-NetTCPConnection -State Listen -LocalPort $entry.Value -ErrorAction SilentlyContinue) {
        Write-Output "$($entry.Key)：端口 $($entry.Value) 已监听，保留现有进程。"
        continue
    }
    $buildJarPath = Join-Path $projectRoot "backend/$($entry.Key)/target/$($entry.Key)-0.1.0-SNAPSHOT.jar"
    $jarPath = Join-Path $runDirectory "$($entry.Key).jar"
    Copy-Item -LiteralPath $buildJarPath -Destination $jarPath -Force
    $stdoutPath = Join-Path $runDirectory "$($entry.Key).out.log"
    $stderrPath = Join-Path $runDirectory "$($entry.Key).err.log"
    $process = Start-Process -FilePath $javaPath -ArgumentList @('-Dfile.encoding=UTF-8', '-Xms128m', '-Xmx512m', '-jar', ('"' + $jarPath + '"'), "--server.port=$($entry.Value)") `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    $state = @{ processId = $process.Id; jarPath = $jarPath; port = $entry.Value; startedAt = (Get-Date).ToString('o') } | ConvertTo-Json
    [IO.File]::WriteAllText((Join-Path $runDirectory "$($entry.Key).json"), $state, [Text.UTF8Encoding]::new($false))
    Write-Output "$($entry.Key)：启动已提交，PID=$($process.Id)，端口=$($entry.Value)"
}
Write-Output '日志和进程记录位于 tmp/local-run/current；运行 scripts/check-environment.ps1 检查就绪状态。'
