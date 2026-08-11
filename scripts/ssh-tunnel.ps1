<#
.SYNOPSIS
启动、停止或检查阿里云开发环境的SSH端口转发。

.EXAMPLE
pwsh -File .\scripts\ssh-tunnel.ps1 start

.EXAMPLE
pwsh -File .\scripts\ssh-tunnel.ps1 status

.EXAMPLE
pwsh -File .\scripts\ssh-tunnel.ps1 stop
#>
[CmdletBinding()]
param(
    [ValidateSet("start", "stop", "status", "restart")]
    [string]$Action = "start",

    [string]$ServerAddress = "8.156.95.5",

    [ValidateRange(1, 65535)]
    [int]$SshPort = 22,

    [string]$SshUser = "root",

    [string]$KeyPath = (Join-Path $env:USERPROFILE ".ssh\road_training_alicloud_ed25519")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$stateDirectory = Join-Path $projectRoot "tmp\ssh-tunnel"
$pidFile = Join-Path $stateDirectory "ssh-tunnel.pid"
$target = "$SshUser@$ServerAddress"

# 本地端口特意避开项目自身端口，所有转发仅监听127.0.0.1。
$forwards = @(
    [PSCustomObject]@{ Name = "MySQL"; LocalPort = 13306; RemotePort = 3306 },
    [PSCustomObject]@{ Name = "Redis"; LocalPort = 16379; RemotePort = 6379 },
    [PSCustomObject]@{ Name = "RabbitMQ AMQP"; LocalPort = 15673; RemotePort = 5672 },
    [PSCustomObject]@{ Name = "RabbitMQ Console"; LocalPort = 25672; RemotePort = 15672 },
    [PSCustomObject]@{ Name = "Nacos Console"; LocalPort = 18000; RemotePort = 8080 },
    [PSCustomObject]@{ Name = "Nacos Server"; LocalPort = 18848; RemotePort = 8848 },
    [PSCustomObject]@{ Name = "Nacos gRPC"; LocalPort = 19848; RemotePort = 9848 }
)

function Get-ListeningConnections {
    return @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue)
}

function Test-IsExpectedTunnelProcess {
    param([object]$ProcessInfo)

    if ($null -eq $ProcessInfo -or [string]::IsNullOrWhiteSpace($ProcessInfo.CommandLine)) {
        return $false
    }

    return $ProcessInfo.Name -eq "ssh.exe" `
        -and $ProcessInfo.CommandLine.Contains($target) `
        -and $ProcessInfo.CommandLine.Contains($KeyPath)
}

function Get-TunnelProcess {
    if (Test-Path -LiteralPath $pidFile) {
        $savedPidText = (Get-Content -LiteralPath $pidFile -Raw).Trim()
        $savedPid = 0
        if ([int]::TryParse($savedPidText, [ref]$savedPid)) {
            $savedProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $savedPid" -ErrorAction SilentlyContinue
            if (Test-IsExpectedTunnelProcess $savedProcess) {
                return $savedProcess
            }
        }
    }

    return Get-CimInstance Win32_Process -Filter "Name = 'ssh.exe'" -ErrorAction SilentlyContinue |
        Where-Object { Test-IsExpectedTunnelProcess $_ } |
        Select-Object -First 1
}

function Save-TunnelPid {
    param([int]$ProcessId)

    New-Item -ItemType Directory -Force -Path $stateDirectory | Out-Null
    $utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($pidFile, [string]$ProcessId, $utf8WithoutBom)
}

function Test-AllForwardsReady {
    param([int]$ProcessId)

    $listeners = Get-ListeningConnections
    foreach ($forward in $forwards) {
        $matched = $listeners | Where-Object {
            $_.LocalAddress -eq "127.0.0.1" `
                -and $_.LocalPort -eq $forward.LocalPort `
                -and $_.OwningProcess -eq $ProcessId
        }
        if (-not $matched) {
            return $false
        }
    }
    return $true
}

function Show-TunnelStatus {
    $tunnelProcess = Get-TunnelProcess
    $listeners = Get-ListeningConnections

    if ($null -eq $tunnelProcess) {
        Write-Host "SSH隧道：未运行"
    }
    else {
        Write-Host "SSH隧道：运行中，PID=$($tunnelProcess.ProcessId)，服务器=$target`:$SshPort"
    }

    foreach ($forward in $forwards) {
        $listener = $listeners | Where-Object {
            $_.LocalAddress -eq "127.0.0.1" -and $_.LocalPort -eq $forward.LocalPort
        } | Select-Object -First 1

        $state = if ($null -eq $listener) {
            "未监听"
        }
        elseif ($null -ne $tunnelProcess -and $listener.OwningProcess -eq $tunnelProcess.ProcessId) {
            "正常"
        }
        else {
            "被其他进程占用，PID=$($listener.OwningProcess)"
        }

        Write-Host ("{0,-18} 127.0.0.1:{1,-5} -> 127.0.0.1:{2,-5} {3}" -f `
                $forward.Name, $forward.LocalPort, $forward.RemotePort, $state)
    }
}

function Start-Tunnel {
    $existingProcess = Get-TunnelProcess
    if ($null -ne $existingProcess) {
        Save-TunnelPid -ProcessId $existingProcess.ProcessId
        if (-not (Test-AllForwardsReady -ProcessId $existingProcess.ProcessId)) {
            Show-TunnelStatus
            throw "检测到不完整的SSH隧道，请执行 restart 重新建立。"
        }
        Write-Host "SSH隧道已经运行，无需重复启动。"
        Show-TunnelStatus
        return
    }

    if (-not (Test-Path -LiteralPath $KeyPath -PathType Leaf)) {
        throw "未找到SSH私钥：$KeyPath"
    }

    $sshCommand = Get-Command ssh.exe -ErrorAction SilentlyContinue
    if ($null -eq $sshCommand) {
        throw "未找到Windows OpenSSH客户端，请先安装OpenSSH Client。"
    }

    $listeners = Get-ListeningConnections
    foreach ($forward in $forwards) {
        $occupied = $listeners | Where-Object {
            $_.LocalAddress -eq "127.0.0.1" -and $_.LocalPort -eq $forward.LocalPort
        } | Select-Object -First 1
        if ($null -ne $occupied) {
            $owner = Get-Process -Id $occupied.OwningProcess -ErrorAction SilentlyContinue
            $ownerName = if ($null -eq $owner) { "未知进程" } else { $owner.ProcessName }
            throw "本地端口$($forward.LocalPort)已被$ownerName占用，PID=$($occupied.OwningProcess)。"
        }
    }

    $sshArguments = @(
        "-N",
        "-T",
        "-p", [string]$SshPort,
        "-i", "`"$KeyPath`"",
        "-o", "BatchMode=yes",
        "-o", "ExitOnForwardFailure=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ServerAliveInterval=30",
        "-o", "ServerAliveCountMax=3"
    )
    foreach ($forward in $forwards) {
        $sshArguments += @(
            "-L",
            "127.0.0.1:$($forward.LocalPort):127.0.0.1:$($forward.RemotePort)"
        )
    }
    $sshArguments += $target

    $startedProcess = Start-Process `
        -FilePath $sshCommand.Source `
        -ArgumentList $sshArguments `
        -WindowStyle Hidden `
        -PassThru

    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        Start-Sleep -Milliseconds 500
        $startedProcess.Refresh()
        if ($startedProcess.HasExited) {
            throw "SSH进程启动失败，退出码为$($startedProcess.ExitCode)。"
        }
        $ready = Test-AllForwardsReady -ProcessId $startedProcess.Id
    } while (-not $ready -and [DateTime]::UtcNow -lt $deadline)

    if (-not $ready) {
        Stop-Process -Id $startedProcess.Id -Force -ErrorAction SilentlyContinue
        throw "SSH进程已启动，但端口转发未在15秒内全部就绪。"
    }

    Save-TunnelPid -ProcessId $startedProcess.Id
    Write-Host "SSH隧道启动成功。"
    Show-TunnelStatus
}

function Stop-Tunnel {
    $tunnelProcess = Get-TunnelProcess
    if ($null -eq $tunnelProcess) {
        if (Test-Path -LiteralPath $pidFile) {
            Remove-Item -LiteralPath $pidFile -Force
        }
        Write-Host "SSH隧道当前未运行。"
        return
    }

    Stop-Process -Id $tunnelProcess.ProcessId -Force
    if (Test-Path -LiteralPath $pidFile) {
        Remove-Item -LiteralPath $pidFile -Force
    }
    Write-Host "SSH隧道已停止，PID=$($tunnelProcess.ProcessId)。"
}

switch ($Action) {
    "start" {
        Start-Tunnel
    }
    "stop" {
        Stop-Tunnel
    }
    "status" {
        Show-TunnelStatus
    }
    "restart" {
        Stop-Tunnel
        Start-Sleep -Seconds 1
        Start-Tunnel
    }
}
