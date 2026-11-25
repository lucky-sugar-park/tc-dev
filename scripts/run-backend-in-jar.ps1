<#
.SYNOPSIS
  WOW-TC 백엔드(tc)를 화면이 든 jar 로 구동 — resources\static 의 화면을 jar 에 넣어 만들고 띄운다.

.DESCRIPTION
  순서:
    1) 화면 확인 — resources\static 에 없으면 멈춘다(먼저 deploy-frontend-in-jar.ps1).
    2) gradlew :tc:bootJar → 화면이 들었는지 확인하고 run\tc\tc.jar.new 로 둔다(-NoBuild 면 건너뛰고 있는 jar 의 화면만 확인).
    3) run-backend.ps1 로 띄운다 — jar 적용·전 jar 보관·기동 확인·정상 종료·실행 폴더가 같다. TC_UI_DIR 는 비워서 jar 안의 화면을 서빙한다.

  run-backend.ps1 과 같은 실행 폴더·pid 를 쓴다 — 한 번에 하나만 뜨고, stop-backend.ps1·stop-all.ps1 로도 멈춘다.
  이미 떠 있으면 재기동하지 않는다(새 jar 는 대기) — 적용은 -Restart. tc 를 재기동하면 tc 를 부르는 MES 등의 PLC 요청이 그동안 끊긴다.
  화면을 바꾸려면 deploy-frontend-in-jar.ps1 → 이 스크립트 -Restart (재기동이 필요하다). 재기동 없이 바꾸려면 폴더 방식(deploy-frontend.ps1·run-backend.ps1).
  같은 jar 를 run-backend.ps1 로 띄우면 run\ui 를 서빙한다.
  사전 조건은 run-backend.ps1 과 같다. Nexus 8081(빌드).

.PARAMETER Port
  서버 포트 (기본 8087).

.PARAMETER NoBuild
  빌드하지 않고 있는 jar(대기 중인 새 jar, 없으면 run\tc\tc.jar)를 띄운다. 화면이 없는 jar 면 멈춘다.

.PARAMETER Foreground
  이 창에서 띄운다(로그가 창에 나온다, Ctrl+C 로 정상 종료).

.PARAMETER Stop
  멈춘다(정상 종료). 빌드하지 않는다.

.PARAMETER Restart
  멈추고(떠 있으면) 다시 띄운다. 뜨지 않으면 전 jar 로 되돌린다(종료 코드 2).

.PARAMETER Status
  실행 여부·health·대기 중인 jar 를 보여 준다. 빌드하지 않는다. 종료 코드 0 = 실행 중, 3 = 멈춤.

.PARAMETER RunDir
  실행 폴더 (기본 레포 루트 run\).

.EXAMPLE
  ./scripts/run-backend-in-jar.ps1
  jar 를 만들어 8087 에 띄운다.

.EXAMPLE
  ./scripts/run-backend-in-jar.ps1 -Restart
  jar 를 새로 만들어 재기동한다.
#>
param(
  [int]$Port = 8087,
  [switch]$NoBuild,
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
$static  = Join-Path $backend 'tc\src\main\resources\static'
$runner  = Join-Path $PSScriptRoot 'run-backend.ps1'
$runRoot = if ($RunDir) { $RunDir } else { Join-Path $repo 'run' }
$tcDir   = Join-Path $runRoot 'tc'
$jar     = Join-Path $tcDir 'tc.jar'
$newJar  = Join-Path $tcDir 'tc.jar.new'
$gradle  = '.\gradlew.bat'
function Step([string]$m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

$common = @{ Port = $Port; StartTimeoutSeconds = $StartTimeoutSeconds; StopTimeoutSeconds = $StopTimeoutSeconds }
if ($RunDir) { $common.RunDir = $RunDir }

if ($Stop)   { & $runner -Stop @common; exit $LASTEXITCODE }
if ($Status) { & $runner -Status @common; exit $LASTEXITCODE }

function Test-JarHasUi([string]$path) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [IO.Compression.ZipFile]::OpenRead($path)
  try { return [bool]($zip.Entries | Where-Object { $_.FullName -eq 'BOOT-INF/classes/static/index.html' } | Select-Object -First 1) }
  finally { $zip.Dispose() }
}

if ($NoBuild) {
  $use = if (Test-Path $newJar) { $newJar } else { $jar }
  if (-not (Test-Path $use)) { throw "띄울 jar 가 없습니다: $use — -NoBuild 없이 실행하세요" }
  if (-not (Test-JarHasUi $use)) { throw "jar 에 화면이 없습니다: $use — ./scripts/deploy-frontend-in-jar.ps1 뒤 -NoBuild 없이 실행하세요" }
} else {
  if (-not (Test-Path (Join-Path $static 'index.html'))) { throw "화면이 없습니다: $static — 먼저 ./scripts/deploy-frontend-in-jar.ps1" }
  Step 'gradlew :tc:bootJar'
  Push-Location $backend
  try {
    & $gradle ':tc:bootJar'
    if ($LASTEXITCODE -ne 0) { throw 'tc jar 빌드 실패' }
  } finally {
    Pop-Location
  }
  $built = Get-ChildItem (Join-Path $backend 'tc\build\libs') -Filter 'tc-*.jar' |
    Where-Object { $_.Name -notlike '*-plain.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $built) { throw 'tc jar 빌드 산출물이 없습니다' }
  if (-not (Test-JarHasUi $built.FullName)) { throw "jar 에 화면이 없습니다: $($built.FullName)" }
  New-Item -ItemType Directory -Force $tcDir | Out-Null
  Copy-Item $built.FullName $newJar -Force
  Write-Host "  새 jar 를 대기시켰습니다: $($built.Name) → $newJar" -ForegroundColor DarkGray
}

if ($Foreground) { & $runner -UiInJar -Foreground @common; exit $LASTEXITCODE }
if ($Restart)    { & $runner -UiInJar -Restart @common; exit $LASTEXITCODE }

& $runner -UiInJar @common
$rc = $LASTEXITCODE
if ($rc -eq 0 -and (Test-Path $newJar)) {
  Write-Host "tc 가 이미 떠 있어 새 jar 는 대기 중입니다. 적용(재기동 — MES 등의 PLC 요청이 잠시 끊긴다): ./scripts/run-backend-in-jar.ps1 -Restart -NoBuild" -ForegroundColor Yellow
}
exit $rc
