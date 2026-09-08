<#
.SYNOPSIS
显式补跑专用OSS或授权照片模型测试；任一缺失条件均失败，不修改本地环境文件。
#>
[CmdletBinding()]
param(
    [ValidateSet('oss', 'face', 'rabbit', 'all')][string]$Suite = 'all',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$EnvironmentFile = '.env.local'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw '请通过-JavaHome指定本机JDK17目录。'
}
$javaVersion = & (Join-Path $JavaHome 'bin/java.exe') -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "17\.') { throw '本项目条件测试要求JDK17。' }
$configPath = if ([IO.Path]::IsPathRooted($EnvironmentFile)) { $EnvironmentFile } else { Join-Path $taskRoot $EnvironmentFile }
Get-Content -LiteralPath $configPath -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        $key = $Matches[1]
        $value = $Matches[2].Trim().Trim([char]34).Trim([char]39)
        if ($null -eq [Environment]::GetEnvironmentVariable($key, 'Process')) {
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
$baseArgs = @('--batch-mode', '--no-transfer-progress', '-f', (Join-Path $taskRoot 'backend/pom.xml'), '-am', '-Dsurefire.failIfNoSpecifiedTests=false')
if ($Suite -in @('rabbit', 'all')) {
    foreach ($key in @('RABBITMQ_HOST', 'RABBITMQ_PORT', 'RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD')) {
        if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) { throw "缺少$key，真实RabbitMQ测试未执行。" }
    }
    $env:RABBITMQ_INTEGRATION_ENABLED = 'true'
    & mvn @baseArgs -pl train-learning-service '-Dtest=LearningOutboxRabbitIntegrationTest' test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if ($Suite -in @('oss', 'all')) {
    foreach ($key in @('OSS_BUCKET', 'OSS_ACCESS_KEY_ID', 'OSS_ACCESS_KEY_SECRET')) {
        if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) { throw "缺少$key，真实OSS测试未执行。" }
    }
    $env:OSS_INTEGRATION_ENABLED = 'true'
    & mvn @baseArgs -pl train-training-service '-Dtest=AliyunOssIntegrationTest' test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if ($Suite -in @('face', 'all')) {
    $properties = [ordered]@{
        'face.detection.model' = 'FACE_DETECTION_MODEL_PATH'
        'face.recognition.model' = 'FACE_RECOGNITION_MODEL_PATH'
        'face.reference.image' = 'TRAIN_FACE_REFERENCE'
        'face.same.image' = 'TRAIN_FACE_SAME_PERSON'
        'face.different.image' = 'TRAIN_FACE_DIFFERENT_PERSON'
        'face.multiple.image' = 'TRAIN_FACE_MULTIPLE'
    }
    $faceArgs = @('-Dface.integration.enabled=true')
    foreach ($entry in $properties.GetEnumerator()) {
        $value = [Environment]::GetEnvironmentVariable($entry.Value, 'Process')
        if (-not $value) { throw "缺少$($entry.Value)，真实模型测试未执行。" }
        $path = [IO.Path]::GetFullPath($value, $taskRoot)
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "$($entry.Value)文件不存在。" }
        $faceArgs += "-D$($entry.Key)=$path"
    }
    & mvn @baseArgs -pl train-face-adapter '-Dtest=OpenCvFaceVerifierIntegrationTest' @faceArgs test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
