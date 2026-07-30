param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "..\backend\train-face-adapter\models"),
    [switch]$Force
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$models = @(
    @{
        Name = "face_detection_yunet_2023mar.onnx"
        Url = "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
        Sha256 = "8F2383E4DD3CFBB4553EA8718107FC0423210DC964F9F4280604804ED2552FA4"
    },
    @{
        Name = "face_recognition_sface_2021dec.onnx"
        Url = "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx"
        Sha256 = "0BA9FBFA01B5270C96627C4EF784DA859931E02F04419C829E83484087C34E79"
    }
)

$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutputDirectory | Out-Null

foreach ($model in $models) {
    $targetPath = Join-Path $resolvedOutputDirectory $model.Name
    if (Test-Path -LiteralPath $targetPath) {
        $existingHash = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash
        if ($existingHash -eq $model.Sha256) {
            Write-Host "模型已存在且校验通过: $targetPath"
            continue
        }
        if (-not $Force) {
            throw "模型文件已存在但SHA-256不匹配，确认后使用 -Force 覆盖: $targetPath"
        }
    }

    $temporaryPath = "$targetPath.download"
    try {
        Write-Host "正在下载 $($model.Name)..."
        Invoke-WebRequest -Uri $model.Url -OutFile $temporaryPath
        $downloadHash = (Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash
        if ($downloadHash -ne $model.Sha256) {
            throw "模型SHA-256校验失败: $($model.Name)"
        }
        Move-Item -LiteralPath $temporaryPath -Destination $targetPath -Force
        Write-Host "模型下载完成: $targetPath"
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

Write-Host ""
Write-Host "请配置以下环境变量为绝对路径:"
Write-Host "FACE_DETECTION_MODEL_PATH=$resolvedOutputDirectory\face_detection_yunet_2023mar.onnx"
Write-Host "FACE_RECOGNITION_MODEL_PATH=$resolvedOutputDirectory\face_recognition_sface_2021dec.onnx"
