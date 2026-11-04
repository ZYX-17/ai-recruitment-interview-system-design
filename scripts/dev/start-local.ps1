<#
.SYNOPSIS
  本地启动 A 域服务栈（无 Nacos）：auth-service / interview-service / notify-service / api-gateway。
.DESCRIPTION
  读取 scripts/dev/env.local.ps1（存在时），打包后依次启动四个 jar。
  日志写入 logs/local/<service>.log，PID 写入 logs/local/pids.txt。
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/dev/start-local.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/dev/start-local.ps1 -SkipBuild -NoGateway
#>
param(
    [switch]$SkipBuild,
    [switch]$NoGateway,
    [int]$HealthTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$EnvFile = Join-Path $PSScriptRoot "env.local.ps1"
if (Test-Path $EnvFile) {
    . $EnvFile
    Write-Host "已加载 $EnvFile"
}

if (-not $env:SEED_ADMIN_PASSWORD) {
    throw "请先设置 SEED_ADMIN_PASSWORD（至少 8 位）；可复制 scripts/dev/env.local.example.ps1 为 env.local.ps1"
}
if (-not $env:JWT_PRIVATE_KEY_PATH) { $env:JWT_PRIVATE_KEY_PATH = Join-Path $Root "infra\secrets\jwt-private.pem" }
if (-not $env:JWT_PUBLIC_KEY_PATH) { $env:JWT_PUBLIC_KEY_PATH = Join-Path $Root "infra\secrets\jwt-public.pem" }
if (-not (Test-Path $env:JWT_PRIVATE_KEY_PATH)) { throw "找不到 JWT 私钥：$env:JWT_PRIVATE_KEY_PATH；先执行 bash scripts/dev/gen-jwt-keys.sh" }
if (-not (Test-Path $env:JWT_PUBLIC_KEY_PATH)) { throw "找不到 JWT 公钥：$env:JWT_PUBLIC_KEY_PATH" }
if (-not $env:INTERNAL_TOKEN) { $env:INTERNAL_TOKEN = "dev-internal-token-please-change" }
if (-not $env:AUTH_SERVICE_URL) { $env:AUTH_SERVICE_URL = "http://127.0.0.1:8081" }
if (-not $env:MAIL_ENABLED) { $env:MAIL_ENABLED = "false" }
if (-not $env:COOKIE_SECURE) { $env:COOKIE_SECURE = "false" }

$LogDir = Join-Path $Root "logs\local"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$PidFile = Join-Path $LogDir "pids.txt"
if (Test-Path $PidFile) { [System.IO.File]::Delete($PidFile) }

if (-not $SkipBuild) {
    $Mvn = if ($env:MAVEN_HOME) { Join-Path $env:MAVEN_HOME "bin\mvn.cmd" } else { "mvn.cmd" }
    Write-Host "打包后端（离线）：$Mvn"
    & $Mvn -o -B -ntp -f (Join-Path $Root "backend\pom.xml") `
        -pl auth-service,interview-service,notify-service,api-gateway -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败，请检查上方输出" }
}

function Start-ServiceJar {
    param([string]$Name, [string]$Profiles, [int]$Port)
    $Jar = Join-Path $Root "backend\$Name\target\$Name.jar"
    if (-not (Test-Path $Jar)) {
        throw "找不到 $Jar；请先不带 -SkipBuild 执行一次，或手动 mvn package"
    }
    $Log = Join-Path $LogDir "$Name.log"
    $ErrLog = Join-Path $LogDir "$Name.err.log"
    $ArgList = @("-jar", $Jar)
    if ($Profiles) { $ArgList += "--spring.profiles.active=$Profiles" }
    $Proc = Start-Process -FilePath "java" -ArgumentList $ArgList -WorkingDirectory $Root `
        -RedirectStandardOutput $Log -RedirectStandardError $ErrLog -WindowStyle Hidden -PassThru
    Add-Content -Path $PidFile -Value "$Name=$($Proc.Id)" -Encoding ASCII
    Write-Host ("启动 {0,-18} PID={1,-7} 端口={2} 日志={3}" -f $Name, $Proc.Id, $Port, $Log)
}

function Wait-Health {
    param([string]$Name, [string]$Url, [int]$TimeoutSeconds)
    $Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $Deadline) {
        try {
            $Resp = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($Resp.StatusCode -eq 200) {
                Write-Host "健康检查通过：$Name"
                return $true
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    Write-Warning "健康检查超时：$Name（$Url），请查看 logs/local/$Name.log"
    return $false
}

Write-Host "== 启动顺序：auth -> interview -> notify -> gateway =="
Start-ServiceJar -Name "auth-service" -Profiles "demo" -Port 8081
Wait-Health -Name "auth-service" -Url "http://127.0.0.1:8081/actuator/health" -TimeoutSeconds $HealthTimeoutSeconds | Out-Null

Start-ServiceJar -Name "interview-service" -Profiles "demo" -Port 8083
Start-ServiceJar -Name "notify-service" -Profiles "" -Port 8086
if (-not $NoGateway) {
    Start-ServiceJar -Name "api-gateway" -Profiles "local" -Port 8080
}

Wait-Health -Name "interview-service" -Url "http://127.0.0.1:8083/actuator/health" -TimeoutSeconds $HealthTimeoutSeconds | Out-Null
Wait-Health -Name "notify-service" -Url "http://127.0.0.1:8086/actuator/health" -TimeoutSeconds $HealthTimeoutSeconds | Out-Null
if (-not $NoGateway) {
    Wait-Health -Name "api-gateway" -Url "http://127.0.0.1:8080/actuator/health" -TimeoutSeconds $HealthTimeoutSeconds | Out-Null
}

Write-Host ""
Write-Host "启动完成。下一步："
Write-Host "  `$env:SMOKE_PASSWORD = `$env:SEED_ADMIN_PASSWORD; bash scripts/smoke.sh"
Write-Host "停止：powershell -ExecutionPolicy Bypass -File scripts/dev/stop-local.ps1"
