[CmdletBinding()]
param(
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot ".keys"
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
$privateKeyPath = Join-Path $resolvedOutput "jwt-private.pem"
$publicKeyPath = Join-Path $resolvedOutput "jwt-public.pem"

New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
if ((Test-Path -LiteralPath $privateKeyPath) -or (Test-Path -LiteralPath $publicKeyPath)) {
    throw "目标密钥已存在，请更换输出目录或先自行备份：$resolvedOutput"
}

function Write-PemFile {
    param(
        [string]$Path,
        [string]$Label,
        [byte[]]$Bytes
    )

    $base64 = [Convert]::ToBase64String($Bytes)
    $lines = for ($offset = 0; $offset -lt $base64.Length; $offset += 64) {
        $length = [Math]::Min(64, $base64.Length - $offset)
        $base64.Substring($offset, $length)
    }
    $content = "-----BEGIN $Label-----`n$($lines -join "`n")`n-----END $Label-----`n"
    [IO.File]::WriteAllText($Path, $content, [Text.UTF8Encoding]::new($false))
}

# 私钥使用PKCS#8格式，公钥使用X.509 SubjectPublicKeyInfo格式。
$rsa = [Security.Cryptography.RSA]::Create()
try {
    $rsa.KeySize = 2048
    Write-PemFile -Path $privateKeyPath -Label "PRIVATE KEY" -Bytes $rsa.ExportPkcs8PrivateKey()
    Write-PemFile -Path $publicKeyPath -Label "PUBLIC KEY" -Bytes $rsa.ExportSubjectPublicKeyInfo()
}
finally {
    $rsa.Dispose()
}

Write-Host "JWT密钥已生成，请勿提交私钥："
Write-Host "JWT_PRIVATE_KEY_PATH=$privateKeyPath"
Write-Host "JWT_PUBLIC_KEY_PATH=$publicKeyPath"
