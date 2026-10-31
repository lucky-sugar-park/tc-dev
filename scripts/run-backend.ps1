<#
.SYNOPSIS
  WOW-TC 백엔드(tc) 구동 — 실행 폴더(run\)의 jar 를 띄우고·멈추고·재기동한다.

.DESCRIPTION
  실행 폴더(기본 레포 루트 run\, git 제외):
    run\tc\tc.jar        띄우는 jar. 없으면 빌드 산출물(wow-tc-backend\tc\build\libs)을 복사해 쓴다
    run\tc\tc.jar.new    적용 대기 중인 새 jar(deploy-backend.ps1 이 Nexus 에서 받아 둔다) — 기동·재기동 때 바꿔 끼운다
    run\tc\tc.jar.prev   바로 전 jar — 새 jar 로 뜨지 않으면 이것으로 되돌린다
    run\ui               화면(deploy-frontend.ps1 이 넣는다) — TC_UI_DIR 로 서빙
    run\logs             tc.out.log / tc.err.log (직전 것은 .1)
    run\local\           로컬 비밀값(jwt-secret.txt·secret-key.txt) — 처음 기동 때 만든다

  비밀값·설정:
    - DB 비밀번호 등은 wow-tc-backend\tc\src\main\resources\application-local.yml(git 제외)을 읽는다(실행 jar 에는 빠져 있다).
    - TC_JWT_SECRET·TC_SECRET_KEY 는 환경 변수가 있으면 그것을, 없으면 run\local\ 의 값을 쓴다(없으면 만든다 — 바꾸면 다시 로그인).
    - 그 밖의 설정은 환경 변수(TC_*)로 준다 — 이 창의 환경 변수가 그대로 넘어간다(docs/operations.md §2.1).

  멈출 때는 정상 종료(진행 중 PLC 요청을 마치고 PLC 연결을 닫는다)를 쓴다. Windows 에는 SIGTERM 이 없어 로컬 JMX 로 Spring 에
  종료를 요청한다(spring.application.admin.enabled). 90초 안에 끝나지 않을 때만 강제로 끝낸다.
  tc 를 재기동하면 tc 를 부르는 MES 등의 PLC 요청이 그동안 끊긴다.

  사전 조건(로컬 인프라): MariaDB 3306 · Redis 6379. JDK 25(java).

.PARAMETER Port
  서버 포트 (기본 8087).

.PARAMETER Foreground
  이 창에서 띄운다(로그가 창에 나온다, Ctrl+C 로 정상 종료). 기본은 백그라운드 + 기동 확인(/api/health).

.PARAMETER Stop
  멈춘다(정상 종료).

.PARAMETER Restart
  멈추고(떠 있으면) 다시 띄운다. 대기 중인 새 jar 가 있으면 적용한다.

.PARAMETER Status
  실행 여부·health·대기 중인 jar 를 보여 준다. 종료 코드 0 = 실행 중, 3 = 멈춤.

.PARAMETER RunDir
  실행 폴더 (기본 레포 루트 run\).

.EXAMPLE
  ./scripts/run-backend.ps1
  백그라운드로 8087 에 띄우고 기동을 확인한다.

.EXAMPLE
  ./scripts/run-backend.ps1 -Restart
  재기동(대기 중인 새 jar 적용, 뜨지 않으면 전 jar 로 되돌림 — 종료 코드 2).

.EXAMPLE
  ./scripts/run-backend.ps1 -Foreground
  이 창에서 띄운다(Ctrl+C 로 종료).
#>
param(
  [int]$Port = 8087,
  [switch]$Foreground,
  [switch]$Stop,
  [switch]$Restart,
  [switch]$Status,
  [string]$RunDir,
  [int]$StartTimeoutSeconds = 120,
  [int]$StopTimeoutSeconds = 90
)
$ErrorActionPreference = 'Stop'

$repo    = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $repo 'wow-tc-backend'
if (-not $RunDir) { $RunDir = Join-Path $repo 'run' }
$tcDir   = Join-Path $RunDir 'tc'
$jar     = Join-Path $tcDir 'tc.jar'
$newJar  = Join-Path $tcDir 'tc.jar.new'
$prevJar = Join-Path $tcDir 'tc.jar.prev'
$pidFile = Join-Path $tcDir 'tc.pid'
$logDir  = Join-Path $RunDir 'logs'
$localDir = Join-Path $RunDir 'local'
$uiDir   = Join-Path $RunDir 'ui'
$localYml = Join-Path $backend 'tc\src\main\resources\application-local.yml'
$shutdownTool = Join-Path $backend 'e2e\ShutdownViaJmx.java'
function Step([string]$m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

function Get-TcProcess {
  if (-not (Test-Path $pidFile)) { return $null }
  $p = Get-Process -Id ([int](Get-Content $pidFile | Select-Object -First 1)) -ErrorAction SilentlyContinue
  if ($p -and $p.ProcessName -eq 'java') { return $p }
  return $null
}

function Get-Health {
  try { return (Invoke-RestMethod -Uri "http://localhost:$Port/api/health" -TimeoutSec 3).status } catch { return $null }
}

# 로컬 비밀값 — 환경 변수가 없으면 run\local\ 의 값(처음이면 만든다). 값은 화면에 내지 않는다
function Get-LocalSecret([string]$envName, [string]$file, [int]$bytes) {
  $v = [Environment]::GetEnvironmentVariable($envName)
  if ($v) { return $v }
  $path = Join-Path $localDir $file
  if (-not (Test-Path $path)) {
    New-Item -ItemType Directory -Force $localDir | Out-Null
    $b = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    [IO.File]::WriteAllText($path, [Convert]::ToBase64String($b))
    Write-Host "  $envName 을(를) 새로 만들었습니다: $path" -ForegroundColor DarkGray
  }
  return (Get-Content $path -Raw).Trim()
}

# 대기 중인 새 jar 를 바꿔 끼운다(멈춰 있을 때만). 바꿨으면 true
function Use-NewJar {
  if (-not (Test-Path $newJar)) { return $false }
  if (Test-Path $jar) { Copy-Item $jar $prevJar -Force }
  Move-Item $newJar $jar -Force
  Write-Host "  새 jar 를 적용했습니다(전 jar: $prevJar)" -ForegroundColor Green
  return $true
}

function Get-JavaArgs {
  $javaArgs = @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-jar', $jar, "--server.port=$Port")
  if (Test-Path $localYml) { $javaArgs += "--spring.config.import=optional:file:$($localYml -replace '\\', '/')" }
  return $javaArgs
}

function Set-TcEnv {
  $env:TC_JWT_SECRET = Get-LocalSecret 'TC_JWT_SECRET' 'jwt-secret.txt' 48
  $env:TC_SECRET_KEY = Get-LocalSecret 'TC_SECRET_KEY' 'secret-key.txt' 32
  $env:TC_SERVER_PORT = "$Port"
  if (-not $env:TC_UI_DIR) { $env:TC_UI_DIR = $uiDir }
}

function Start-Tc {
  if (Get-TcProcess) { Write-Host "tc 가 이미 떠 있습니다(pid $((Get-TcProcess).Id))." -ForegroundColor Yellow; return $true }
  New-Item -ItemType Directory -Force $tcDir, $logDir, $uiDir | Out-Null
  [void](Use-NewJar)
  if (-not (Test-Path $jar)) {
    $built = Get-ChildItem (Join-Path $backend 'tc\build\libs') -Filter 'tc-*.jar' -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -notlike '*-plain.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $built) { throw "띄울 jar 가 없습니다 — 먼저 ./scripts/deploy-backend.ps1 (또는 gradlew :tc:bootJar)" }
    Copy-Item $built.FullName $jar
    Write-Host "  빌드 산출물을 씁니다: $($built.Name)" -ForegroundColor DarkGray
  }
  if (-not (Test-Path $localYml)) { Write-Host "  경고: $localYml 이 없습니다 — DB 비밀번호는 TC_DB_PASSWORD 로 주세요" -ForegroundColor Yellow }

  $out = Join-Path $logDir 'tc.out.log'; $err = Join-Path $logDir 'tc.err.log'
  foreach ($f in $out, $err) { if (Test-Path $f) { Move-Item $f "$f.1" -Force } }
  $saved = @{}; foreach ($k in 'TC_JWT_SECRET', 'TC_SECRET_KEY', 'TC_SERVER_PORT', 'TC_UI_DIR') { $saved[$k] = [Environment]::GetEnvironmentVariable($k) }
  try {
    Set-TcEnv
    $quoted = (Get-JavaArgs | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
    # java 를 cmd 로 띄워 출력은 cmd 가 파일로 돌린다. Start-Process -RedirectStandardOutput 은 이 창의 핸들을 java 에 물려줘서,
    # 이 스크립트 출력을 파이프로 받는 쪽(| Tee-Object 등)이 tc 가 끝날 때까지 멈춘다
    $cmd = Start-Process -FilePath cmd.exe -ArgumentList "/c `"java $quoted 1>`"$out`" 2>`"$err`"`"" -WorkingDirectory $RunDir -WindowStyle Hidden -PassThru
  } finally {
    # 비밀값을 이 창에 남기지 않는다
    foreach ($k in $saved.Keys) { [Environment]::SetEnvironmentVariable($k, $saved[$k]) }
  }
  $javaPid = $null
  for ($i = 0; $i -lt 50 -and -not $javaPid; $i++) {
    $javaPid = (Get-CimInstance Win32_Process -Filter "ParentProcessId=$($cmd.Id) AND Name='java.exe'" | Select-Object -First 1).ProcessId
    if (-not $javaPid) { Start-Sleep -Milliseconds 200 }
  }
  if (-not $javaPid) {
    Write-Host "  java 가 뜨지 않았거나 바로 끝났습니다 — $err" -ForegroundColor Red
    if (Test-Path $err) { Get-Content $err -Tail 10 | ForEach-Object { Write-Host "    $_" } }
    return $false
  }
  $javaPid | Set-Content $pidFile
  Write-Host "  tc 를 띄웠습니다(pid $javaPid, 포트 $Port) — 기동 확인 중" -ForegroundColor DarkGray
  $deadline = (Get-Date).AddSeconds($StartTimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $h = Get-Health
    if ($h -eq 'UP' -or $h -eq 'DEGRADED') { Write-Host "tc 기동 완료 — http://localhost:$Port  (/api/health $h)" -ForegroundColor Green; return $true }
    if (-not (Get-TcProcess)) { break }
    Start-Sleep -Seconds 2
  }
  Write-Host "tc 가 $StartTimeoutSeconds 초 안에 뜨지 않았습니다 — 로그: $out" -ForegroundColor Red
  if (Test-Path $out) { Get-Content $out -Tail 15 -Encoding UTF8 | ForEach-Object { Write-Host "    $_" } }
  return $false
}

function Stop-Tc {
  $p = Get-TcProcess
  if (-not $p) { Write-Host 'tc 가 떠 있지 않습니다.' -ForegroundColor DarkGray; return }
  Write-Host "  정상 종료 요청(pid $($p.Id)) — 진행 중 요청을 마치고 PLC 연결을 닫습니다" -ForegroundColor DarkGray
  & java $shutdownTool $p.Id | Out-Null
  if (-not $p.WaitForExit($StopTimeoutSeconds * 1000)) {
    Write-Host "  $StopTimeoutSeconds 초 안에 끝나지 않아 강제로 끝냅니다" -ForegroundColor Yellow
    Stop-Process -Id $p.Id -Force
    $p.WaitForExit(10000) | Out-Null
  }
  Remove-Item $pidFile -ErrorAction SilentlyContinue
  Write-Host 'tc 를 멈췄습니다.' -ForegroundColor Green
}

if ($Status) {
  $p = Get-TcProcess
  if ($p) { Write-Host "실행 중 — pid $($p.Id), 시작 $($p.StartTime), /api/health $(Get-Health)" } else { Write-Host '멈춰 있음' }
  if (Test-Path $newJar) { Write-Host "적용 대기 중인 새 jar: $newJar — ./scripts/run-backend.ps1 -Restart" -ForegroundColor Yellow }
  if ($p) { exit 0 } else { exit 3 }
}

if ($Stop) { Step 'tc 중지'; Stop-Tc; exit 0 }

if ($Foreground) {
  if (Get-TcProcess) { throw "tc 가 이미 백그라운드로 떠 있습니다 — 먼저 ./scripts/run-backend.ps1 -Stop" }
  Step "tc 구동(포그라운드, 포트 $Port) — 중지: Ctrl+C"
  New-Item -ItemType Directory -Force $tcDir, $uiDir | Out-Null
  [void](Use-NewJar)
  if (-not (Test-Path $jar)) { throw "띄울 jar 가 없습니다 — 먼저 ./scripts/deploy-backend.ps1" }
  Set-TcEnv
  & java @(Get-JavaArgs)
  exit $LASTEXITCODE
}

if ($Restart) {
  Step 'tc 재기동'
  Stop-Tc
  $applied = Test-Path $newJar
  if (Start-Tc) { exit 0 }
  if ($applied -and (Test-Path $prevJar)) {
    Step '새 jar 로 뜨지 않아 전 jar 로 되돌립니다'
    Stop-Tc
    Copy-Item $jar (Join-Path $tcDir 'tc.jar.failed') -Force
    Copy-Item $prevJar $jar -Force
    if (Start-Tc) { Write-Host "전 jar 로 되돌렸습니다. 실패한 jar: $(Join-Path $tcDir 'tc.jar.failed')" -ForegroundColor Yellow; exit 2 }
  }
  exit 1
}

Step "tc 구동(포트 $Port)"
if (Start-Tc) { exit 0 } else { exit 1 }
