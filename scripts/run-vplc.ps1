<#
.SYNOPSIS
  WOW-TC 가상 PLC(vplc) 구동 — 띄우고·멈추고·재기동한다(기본 포트 8088).

.DESCRIPTION
  vplc 는 시험 장비라 Nexus 에 올리지 않는다. 빌드 산출물(wow-tc-backend\vplc\build\libs\vplc-<버전>.jar)을 실행 폴더로 복사해 띄운다 —
  빌드 폴더의 jar 를 바로 띄우면 Windows 가 파일을 잠가 다음 빌드(bootJar)가 실패한다.
    run\vplc\vplc.jar    띄우는 jar(띄울 때마다 빌드 산출물 중 가장 새 것으로 바꾼다)
    run\vplc\vplc.pid
    run\logs\            vplc.out.log / vplc.err.log (직전 것은 .1)
  설정:
    - DB 비밀번호 등은 wow-tc-backend\vplc\src\main\resources\application-local.yml(git 제외)을 읽는다. 없으면 VPLC_DB_PASSWORD.
    - 그 밖의 설정은 이 창의 환경 변수(VPLC_*)로 준다.
  멈출 때는 정상 종료를 쓴다 — Windows 에는 SIGTERM 이 없어 로컬 JMX 로 Spring 에 종료를 요청한다(spring.application.admin.enabled).
  가상 PLC 포트를 닫고 끊김 이력을 남긴다. 60초 안에 끝나지 않거나 JMX 가 꺼진 예전 jar 면 강제로 끝낸다.
  vplc 를 재기동하면 붙어 있던 tc 커넥터가 끊겼다가 다시 붙는다. 게시·실행 중이던 가상 PLC 는 vplc 가 기동하며 되살린다.
  스크립트 없이 띄운 vplc(pid 파일 없음)도 포트를 쥔 프로세스가 vplc jar 면 멈추고 재기동한다.
  사전 조건(로컬 인프라): MariaDB 3306. JDK 25(java).

.PARAMETER Port
  서버 포트 (기본 8088).

.PARAMETER Build
  띄우기 전에 빌드한다(gradlew :vplc:bootJar).

.PARAMETER Foreground
  이 창에서 띄운다(로그가 창에 나온다, Ctrl+C 로 정상 종료).

.PARAMETER Stop
  멈춘다(정상 종료).

.PARAMETER Restart
  멈추고(떠 있으면) 다시 띄운다. -Build 와 같이 쓰면 새로 빌드한 jar 로.

.PARAMETER Status
  실행 여부·가상 PLC 상태를 보여 준다. 종료 코드 0 = 실행 중, 3 = 멈춤.

.PARAMETER RunDir
  실행 폴더 (기본 레포 루트 run\).

.EXAMPLE
  ./scripts/run-vplc.ps1 -Restart -Build
  빌드하고 재기동한다.

.EXAMPLE
  ./scripts/run-vplc.ps1 -Status
#>
param(
  [int]$Port = 8088,
  [switch]$Build,
  [switch]$Foreground,
  [switch]$Stop,
  [switch]$Restart,
  [switch]$Status,
  [string]$RunDir,
  [int]$StartTimeoutSeconds = 90,
  [int]$StopTimeoutSeconds = 60
)
$ErrorActionPreference = 'Stop'

$repo    = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $repo 'wow-tc-backend'
if (-not $RunDir) { $RunDir = Join-Path $repo 'run' }
$vplcDir = Join-Path $RunDir 'vplc'
$jar     = Join-Path $vplcDir 'vplc.jar'
$pidFile = Join-Path $vplcDir 'vplc.pid'
$logDir  = Join-Path $RunDir 'logs'
$libs    = Join-Path $backend 'vplc\build\libs'
$localYml = Join-Path $backend 'vplc\src\main\resources\application-local.yml'
$shutdownTool = Join-Path $backend 'e2e\ShutdownViaJmx.java'
$api     = "http://localhost:$Port/api/v1/vplc"
function Step([string]$m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# 포트를 듣고 있는 프로세스 id(없으면 $null)
function Get-PortOwner {
  $c = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($c) { return [int]$c.OwningProcess }
  return $null
}

function Get-CommandLine([int]$id) {
  $p = Get-CimInstance Win32_Process -Filter "ProcessId=$id" -ErrorAction SilentlyContinue
  if ($p) { return "$($p.CommandLine)" }
  return ''
}

function Get-ProcessLabel([int]$id) {
  $cmdLine = Get-CommandLine $id
  if ($cmdLine.Length -gt 160) { $cmdLine = $cmdLine.Substring(0, 160) + '...' }
  return "pid $id $cmdLine"
}

# 떠 있는 vplc — pid 파일, 없으면 포트를 쥔 프로세스가 vplc jar 일 때(스크립트 없이 띄운 것)
function Get-VplcProcess {
  if (Test-Path $pidFile) {
    $p = Get-Process -Id ([int](Get-Content $pidFile | Select-Object -First 1)) -ErrorAction SilentlyContinue
    if ($p -and $p.ProcessName -eq 'java') { return $p }
  }
  $owner = Get-PortOwner
  if ($owner -and (Get-CommandLine $owner) -match 'vplc[^\\/]*\.jar') { return Get-Process -Id $owner -ErrorAction SilentlyContinue }
  return $null
}

# 가상 PLC 목록(응답하면) — 기동 확인과 상태 표시에 쓴다
function Get-Vplcs {
  try { return @(Invoke-RestMethod -Uri "$api/management/search" -TimeoutSec 3) } catch { return $null }
}

function Get-StartFailure([string]$out, [string]$err) {
  $lines = @()
  if (Test-Path $out) { $lines = @(Get-Content $out -Encoding UTF8) }
  $i = -1
  for ($k = $lines.Count - 1; $k -ge 0; $k--) { if ($lines[$k] -match '^\s*APPLICATION FAILED TO START\s*$') { $i = $k; break } }
  if ($i -ge 0) {
    return @($lines[($i + 1)..($lines.Count - 1)] | Where-Object { $_.Trim() -and $_ -notmatch '^\*+$' } | Select-Object -First 12)
  }
  $cause = $lines | Where-Object { $_ -match '^Caused by:|\s(ERROR|FATAL)\s' } | Select-Object -Last 2
  if ($cause) { return @($cause) }
  if (Test-Path $err) { $e = @(Get-Content $err -Tail 10 -Encoding UTF8 | Where-Object { $_.Trim() }); if ($e) { return $e } }
  return @($lines | Select-Object -Last 15)
}

function Write-StartFailure([string]$summary, [string[]]$detail, [string]$out) {
  Write-Host "vplc 기동 실패 — $summary" -ForegroundColor Red
  if ($detail) { Write-Host '  원인:' -ForegroundColor Red; $detail | ForEach-Object { Write-Host "    $_" } }
  if ($out) { Write-Host "  로그: $out" -ForegroundColor DarkGray }
}

function Invoke-Build {
  Step 'vplc 빌드(gradlew :vplc:bootJar)'
  Push-Location $backend
  try {
    & .\gradlew.bat -q :vplc:bootJar
    if ($LASTEXITCODE -ne 0) { throw "빌드 실패(종료 코드 $LASTEXITCODE)" }
  } finally { Pop-Location }
}

# 빌드 산출물 중 가장 새 jar 를 실행 폴더로(멈춰 있을 때만)
function Copy-BuiltJar {
  $built = Get-ChildItem $libs -Filter 'vplc-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*-plain.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $built) {
    if (Test-Path $jar) { Write-Host "  빌드 산출물이 없어 실행 폴더의 jar 를 그대로 씁니다" -ForegroundColor Yellow; return }
    throw "띄울 jar 가 없습니다 — ./scripts/run-vplc.ps1 -Build"
  }
  New-Item -ItemType Directory -Force $vplcDir | Out-Null
  Copy-Item $built.FullName $jar -Force
  Write-Host "  $($built.Name) ($($built.LastWriteTime.ToString('yyyy-MM-dd HH:mm'))) 를 씁니다" -ForegroundColor DarkGray
}

function Get-JavaArgs {
  $javaArgs = @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-jar', $jar, "--server.port=$Port")
  if (Test-Path $localYml) { $javaArgs += "--spring.config.import=optional:file:$($localYml -replace '\\', '/')" }
  return $javaArgs
}

function Start-Vplc {
  if ($p = Get-VplcProcess) { Write-Host "vplc 가 이미 떠 있습니다(pid $($p.Id))." -ForegroundColor Yellow; return $true }
  if ($owner = Get-PortOwner) {
    Write-StartFailure "포트 $Port 를 이미 다른 프로세스가 쓰고 있습니다" @((Get-ProcessLabel $owner), "그 프로세스를 멈추거나 -Port 로 다른 포트를 주세요")
    return $false
  }
  New-Item -ItemType Directory -Force $vplcDir, $logDir | Out-Null
  Copy-BuiltJar
  if (-not (Test-Path $localYml)) { Write-Host "  경고: $localYml 이 없습니다 — DB 비밀번호는 VPLC_DB_PASSWORD 로 주세요" -ForegroundColor Yellow }

  $out = Join-Path $logDir 'vplc.out.log'; $err = Join-Path $logDir 'vplc.err.log'
  foreach ($f in $out, $err) { if (Test-Path $f) { Move-Item $f "$f.1" -Force } }
  $quoted = (Get-JavaArgs | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
  # java 를 cmd 로 띄워 출력은 cmd 가 파일로 돌린다(run-backend.ps1 과 같은 까닭 — 이 창의 핸들을 java 에 물려주지 않는다)
  $cmd = Start-Process -FilePath cmd.exe -ArgumentList "/c `"java $quoted 1>`"$out`" 2>`"$err`"`"" -WorkingDirectory $RunDir -WindowStyle Hidden -PassThru
  $javaPid = $null
  for ($i = 0; $i -lt 50 -and -not $javaPid; $i++) {
    $javaPid = (Get-CimInstance Win32_Process -Filter "ParentProcessId=$($cmd.Id) AND Name='java.exe'" | Select-Object -First 1).ProcessId
    if (-not $javaPid) { Start-Sleep -Milliseconds 200 }
  }
  if (-not $javaPid) { Write-StartFailure 'java 가 뜨지 않았거나 바로 끝났습니다' (Get-StartFailure $out $err) $err; return $false }
  $javaPid | Set-Content $pidFile
  Write-Host "  vplc 를 띄웠습니다(pid $javaPid, 포트 $Port) — 기동 확인 중" -ForegroundColor DarkGray
  $deadline = (Get-Date).AddSeconds($StartTimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (-not (Get-Process -Id $javaPid -ErrorAction SilentlyContinue)) {
      Remove-Item $pidFile -ErrorAction SilentlyContinue
      Write-StartFailure '기동 중에 끝났습니다' (Get-StartFailure $out $err) $out
      return $false
    }
    if ((Get-PortOwner) -eq $javaPid -and ($list = Get-Vplcs) -ne $null) {
      Write-Host "vplc 기동 완료 — $api  (pid $javaPid)" -ForegroundColor Green
      Show-Vplcs $list
      return $true
    }
    Start-Sleep -Seconds 2
  }
  Write-StartFailure "$StartTimeoutSeconds 초 안에 응답하지 않습니다(pid $javaPid 는 아직 살아 있음 — ./scripts/run-vplc.ps1 -Stop)" (Get-StartFailure $out $err) $out
  return $false
}

function Show-Vplcs($list) {
  if (-not $list) { return }
  $list | ForEach-Object {
    $protocol = if ($_.protocol) { $_.protocol } else { 'MELSEC_MC' }
    $ports = if ($_.portCount -gt 1) { "$($_.startPort)~$($_.startPort + $_.portCount - 1)" } else { "$($_.startPort)" }
    Write-Host ("  {0,-20} {1,-11} {2,-10} {3}" -f $_.name, $protocol, $_.status, $ports)
  }
}

function Stop-Vplc {
  $p = Get-VplcProcess
  if (-not $p) { Write-Host 'vplc 가 떠 있지 않습니다.' -ForegroundColor DarkGray; return }
  Write-Host "  정상 종료 요청(pid $($p.Id)) — 가상 PLC 포트를 닫고 끊김 이력을 남깁니다" -ForegroundColor DarkGray
  # Windows PowerShell 5.1 은 Stop 상태에서 java 의 stderr 를 오류로 멈춘다 — 이 줄만 이어 가고 종료 코드로 본다
  $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  & java $shutdownTool $p.Id 2>&1 | Out-Null
  $jmxExit = $LASTEXITCODE
  $ErrorActionPreference = $eap
  if ($jmxExit -ne 0) {
    # JMX(spring.application.admin)가 꺼진 예전 jar
    Write-Host '  정상 종료를 요청할 수 없어(JMX 꺼짐) 강제로 끝냅니다' -ForegroundColor Yellow
    Stop-Process -Id $p.Id -Force
  } elseif (-not $p.WaitForExit($StopTimeoutSeconds * 1000)) {
    Write-Host "  $StopTimeoutSeconds 초 안에 끝나지 않아 강제로 끝냅니다" -ForegroundColor Yellow
    Stop-Process -Id $p.Id -Force
  }
  $p.WaitForExit(10000) | Out-Null
  Remove-Item $pidFile -ErrorAction SilentlyContinue
  Write-Host 'vplc 를 멈췄습니다.' -ForegroundColor Green
}

if ($Status) {
  $p = Get-VplcProcess
  if ($p) {
    Write-Host "실행 중 — pid $($p.Id), 시작 $($p.StartTime), $api"
    Show-Vplcs (Get-Vplcs)
  } else { Write-Host '멈춰 있음' }
  $owner = Get-PortOwner
  if ($owner -and (-not $p -or $owner -ne $p.Id)) { Write-Host "포트 $Port 를 다른 프로세스가 쓰고 있습니다: $(Get-ProcessLabel $owner)" -ForegroundColor Yellow }
  if ($p) { exit 0 } else { exit 3 }
}

if ($Stop) { Step 'vplc 중지'; Stop-Vplc; exit 0 }

if ($Build) { Invoke-Build }

if ($Foreground) {
  if (Get-VplcProcess) { throw "vplc 가 이미 떠 있습니다 — 먼저 ./scripts/run-vplc.ps1 -Stop" }
  if ($owner = Get-PortOwner) { throw "포트 $Port 를 이미 다른 프로세스가 쓰고 있습니다 — $(Get-ProcessLabel $owner)" }
  Step "vplc 구동(포그라운드, 포트 $Port) — 중지: Ctrl+C"
  Copy-BuiltJar
  & java @(Get-JavaArgs)
  exit $LASTEXITCODE
}

if ($Restart) {
  Step 'vplc 재기동'
  Stop-Vplc
  if (Start-Vplc) { exit 0 } else { exit 1 }
}

Step "vplc 구동(포트 $Port)"
if (Start-Vplc) { exit 0 } else { exit 1 }
