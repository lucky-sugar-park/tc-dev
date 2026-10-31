<#
.SYNOPSIS
  WOW-TC 백엔드(tc) 빌드 → Nexus 배포 → 재기동.

.DESCRIPTION
  순서:
    1) (옵션) clean (-Clean)
    2) gradlew :tc:build — 빌드(+단위 테스트, 인프라 없이 돈다). 실행 jar(bootJar)를 만든다.
    3) gradlew :tc:publish — 실행 jar 를 사내 Nexus 에 com.wow.be:wow-tc:<버전> 으로 올린다
         (버전이 SNAPSHOT 이면 inzi-mes-platform-maven-private-snapshot, 아니면 inzi-mes-platform-maven-private).
    4) Nexus(group)에서 방금 올린 jar 를 내려받아 빌드한 것과 같은지(SHA-256) 확인하고 run\tc\tc.jar.new 로 둔다
         — 서버가 Nexus 에서 받아 띄우는 것과 같은 길을 밟는다.
    5) (기본) run-backend.ps1 -Restart — 정상 종료 → 새 jar 적용 → 기동 확인. 뜨지 않으면 전 jar 로 되돌린다(종료 코드 2).
       tc 를 재기동하면 tc 를 부르는 MES 등의 PLC 요청이 그동안 끊긴다.

  사전 조건:
    - Nexus 8081 + 자격증명(전역 ~/.gradle/gradle.properties 의 nexusUsername/nexusPassword — mes-dev 와 같다)
    - 기동에는 MariaDB 3306 · Redis 6379, wow-tc-backend\tc\src\main\resources\application-local.yml(DB 비밀번호)
    - DB 스크립트(wow-tc-backend\migration)는 돌리지 않는다 — 새 스크립트가 있으면 재기동 전에 돌린다(docs/operations.md §3.1).

.PARAMETER SkipTests
  테스트를 건너뜀 (gradle '-x test').

.PARAMETER SkipPublish
  Nexus 업로드·내려받기를 건너뜀 — 빌드한 jar 를 바로 run\tc\tc.jar.new 로 둔다(Nexus 없이 로컬 확인).

.PARAMETER SkipRestart
  재기동하지 않는다 — 새 jar 는 run\tc\tc.jar.new 로 대기(다음 run-backend.ps1 -Restart 때 적용).

.PARAMETER Clean
  빌드 전에 clean 수행.

.PARAMETER Port
  재기동할 tc 포트 (기본 8087).

.EXAMPLE
  ./scripts/deploy-backend.ps1
  빌드(+테스트) → Nexus publish → 받아서 재기동.

.EXAMPLE
  ./scripts/deploy-backend.ps1 -SkipTests -SkipRestart
  빠르게 빌드·publish 하고 새 jar 는 대기만(재기동은 나중에).

.EXAMPLE
  ./scripts/deploy-backend.ps1 -SkipPublish
  Nexus 없이 빌드 → 재기동.
#>
param(
  [switch]$SkipTests,
  [switch]$SkipPublish,
  [switch]$SkipRestart,
  [switch]$Clean,
  [int]$Port = 8087
)
$ErrorActionPreference = 'Stop'

$repo    = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$be      = Join-Path $repo 'wow-tc-backend'
$tcDir   = Join-Path $repo 'run\tc'
$gradle  = if ($env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
function Step([string]$m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# 버전·Nexus 주소는 build.gradle 한 곳에서 읽는다
$buildGradle = Get-Content -Raw (Join-Path $be 'build.gradle')
$version  = ([regex]::Match($buildGradle, "version\s*=\s*'([^']+)'")).Groups[1].Value
$groupUrl = ([regex]::Match($buildGradle, "nexusGroupUrl\s*=\s*'([^']+)'")).Groups[1].Value
if (-not $version -or -not $groupUrl) { throw 'build.gradle 에서 version·nexusGroupUrl 을 읽지 못했습니다' }

$gargs = @()
if ($Clean) { $gargs += ':tc:clean' }
$gargs += ':tc:build'
if (-not $SkipPublish) { $gargs += ':tc:publish' }
if ($SkipTests) { $gargs += '-x'; $gargs += 'test' }

Push-Location $be
try {
  Step "$gradle $($gargs -join ' ')"
  if ($SkipTests) { Write-Host '(테스트 생략: -x test)' -ForegroundColor DarkGray }
  & $gradle @gargs
  if ($LASTEXITCODE -ne 0) { throw "gradle 실패 (exit $LASTEXITCODE). Nexus 8081/자격증명을 확인하세요." }
} finally {
  Pop-Location
}

$built = Join-Path $be "tc\build\libs\tc-$version.jar"
if (-not (Test-Path $built)) { throw "빌드 산출물 없음: $built" }
New-Item -ItemType Directory -Force $tcDir | Out-Null
$staged = Join-Path $tcDir 'tc.jar.new'

if ($SkipPublish) {
  Write-Host 'publish 건너뜀 (-SkipPublish) — 빌드한 jar 를 씁니다.' -ForegroundColor Yellow
  Copy-Item $built $staged -Force
} else {
  $url = "$($groupUrl.TrimEnd('/'))/com/wow/be/wow-tc/$version/wow-tc-$version.jar"
  Step "Nexus 에서 받기 : $url"
  $ProgressPreference = 'SilentlyContinue'   # Windows PowerShell 5.1 은 진행 막대 때문에 큰 파일이 매우 느리다
  Invoke-WebRequest -Uri $url -OutFile $staged -UseBasicParsing
  $want = (Get-FileHash $built -Algorithm SHA256).Hash
  $got  = (Get-FileHash $staged -Algorithm SHA256).Hash
  if ($want -ne $got) {
    Remove-Item $staged
    throw "Nexus 의 jar 가 방금 빌드한 것과 다릅니다(같은 버전 재배포를 막는 저장소이거나 캐시) — 버전을 올리거나 Nexus 설정을 확인하세요"
  }
  Write-Host "  받은 jar 가 빌드한 것과 같습니다 (SHA-256 $($got.Substring(0, 12))…)" -ForegroundColor Green
}

$migrations = @(Get-ChildItem (Join-Path $be 'migration') -Filter *.sql | Sort-Object Name | ForEach-Object Name)
Write-Host "  DB 스크립트(wow-tc-backend\migration, 마지막 $($migrations[-1])) — 새 것이 있으면 재기동 전에 돌리세요." -ForegroundColor DarkGray

if ($SkipRestart) {
  Step '완료 — 재기동 건너뜀 (-SkipRestart)'
  Write-Host "새 jar 대기: $staged" -ForegroundColor Yellow
  Write-Host '적용(재기동 — MES 등의 PLC 요청이 잠시 끊긴다): ./scripts/run-backend.ps1 -Restart' -ForegroundColor Yellow
  exit 0
}

& (Join-Path $PSScriptRoot 'run-backend.ps1') -Restart -Port $Port
$rc = $LASTEXITCODE
if ($rc -eq 0) { Step '완료'; Write-Host "tc $version 배포·재기동 끝" -ForegroundColor Green }
exit $rc
