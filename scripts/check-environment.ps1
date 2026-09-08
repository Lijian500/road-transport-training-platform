<#
.SYNOPSIS
只读检查本地服务与开发基础设施转发端口，失败时返回非零退出码。
#>
[CmdletBinding()]
param([switch]$InfrastructureOnly)
$ErrorActionPreference = 'Stop'
$targets = [ordered]@{ MySQL = 13306; Redis = 16379; RabbitMQ = 15673; Nacos = 18848 }
if (-not $InfrastructureOnly) {
    $targets['Admin'] = 8091; $targets['Training'] = 8092; $targets['Learning'] = 8093
    $targets['WebApi'] = 8081; $targets['Realtime'] = 8082; $targets['Gateway'] = 8080
}
$failed = $false
foreach ($target in $targets.GetEnumerator()) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync('127.0.0.1', $target.Value)
        if (-not $connect.Wait(2000) -or -not $client.Connected) { throw '连接超时' }
        Write-Output "$($target.Key)：127.0.0.1:$($target.Value) 可连接"
    } catch { $failed = $true; Write-Output "$($target.Key)：未就绪" }
    finally { $client.Dispose() }
}
if ($failed) { exit 1 }
