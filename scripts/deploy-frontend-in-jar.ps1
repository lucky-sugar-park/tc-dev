<#
.SYNOPSIS
  WOW-TC 화면(wow-tc-frontend) 빌드 → tc 프로젝트 resources\static 으로 복사(화면이 tc.jar 안에 함께 들어간다).

.DESCRIPTION
  순서:
    1) (옵션) npm install (-Install)
    2) npm run build — tsc 검사 + vite build → wow-tc-frontend\dist
    3) dist 를 wow-tc-backend\tc\src\main\resources\static 에 맞춘다(git 제외). 이후 만드는 tc jar 와 IDE(Eclipse) 실행이 이 화면을 쓴다.
    4) tc-config.json(가상 PLC 주소)은 덮지 않는다. 없으면 만든다(기본 vplcUrl http://localhost:8088).
         dist 의 tc-config.json(개발 서버용 /vplc-api)은 복사하지 않는다.

  이 스크립트는 떠 있는 tc 에 반영하지 않는다 — jar 로 띄우려면 run-backend-in-jar.ps1 로 새로 만들어 띄운다. Eclipse 는 프로젝트를 새로 고친(F5) 뒤 tc 를 다시 실행한다.
  tc 는 TC_UI_DIR 가 비어 있을 때만 jar 안의 화면을 쓴다(값이 있으면 그 폴더). 폴더 방식은 deploy-frontend.ps1·run-backend.ps1.
  resources\static 이 남아 있으면 deploy-backend.ps1 이 만드는 jar 에도 화면이 들어간다(run-backend.ps1 은 run\ui 를 서빙하므로 동작은 같다).

.PARAMETER Install
  빌드 전에 npm install 실행 (의존성 변경 시).

.PARAMETER VplcUrl
  tc-config.json 의 가상 PLC 주소를 이 값으로 쓴다(주면 기존 값을 덮는다). vplc 의 CORS 허용 출처에 tc 화면 주소가 있어야 한다.

.PARAMETER NoVplc
  tc-config.json 을 {} 로 쓴다 — 가상 PLC 메뉴를 숨긴다(운영).

.EXAMPLE
  ./scripts/deploy-frontend-in-jar.ps1
  빌드 → resources\static 복사.

.EXAMPLE
  ./scripts/deploy-frontend-in-jar.ps1 -NoVplc
  가상 PLC 메뉴 없이.
#>
param(
  [switch]$Install,
  [string]$VplcUrl,
  [switch]$NoVplc
)
$ErrorActionPreference = 'Stop'

$repo   = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$fe     = Join-Path $repo 'wow-tc-frontend'
$dist   = Join-Path $fe 'dist'
$static = Join-Path $repo 'wow-tc-backend\tc\src\main\resources\static'
function Step([string]$m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

function Get-IndexBundle([string]$indexHtml) {
  if (-not (Test-Path $indexHtml)) { return '' }
  return [regex]::Match((Get-Content -Raw $indexHtml), 'assets/index-[^"]+\.js').Value
}

Push-Location $fe
try {
  if ($Install) {
    Step 'npm install'
    npm install
    if ($LASTEXITCODE -ne 0) { throw 'npm install 실패 (Nexus 8081 npm 저장소를 확인하세요)' }
  }
  Step 'build (tsc + vite)'
  npm run build
  if ($LASTEXITCODE -ne 0) { throw '화면 빌드 실패' }
  if (-not (Test-Path (Join-Path $dist 'index.html'))) { throw "빌드 산출물 없음: $dist" }
} finally {
  Pop-Location
}

Step "copy → $static"
New-Item -ItemType Directory -Force $static | Out-Null
# robocopy 종료 코드는 8 미만이 성공. tc-config.json 은 건드리지 않는다
robocopy $dist $static /MIR /XF tc-config.json /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy 실패(rc=$LASTEXITCODE): $dist → $static" }

$want = Get-IndexBundle (Join-Path $dist 'index.html')
$got  = Get-IndexBundle (Join-Path $static 'index.html')
if ($want -ne $got) { throw "index.html 이 dist 와 다릅니다($static): $got ≠ $want" }

$cfg = Join-Path $static 'tc-config.json'
$utf8 = New-Object System.Text.UTF8Encoding($false)   # BOM 없이(브라우저가 JSON 으로 읽는다)
if ($NoVplc) {
  [IO.File]::WriteAllText($cfg, '{}', $utf8)
  Write-Host '  tc-config.json = {} (가상 PLC 메뉴 숨김)' -ForegroundColor DarkGray
} elseif ($VplcUrl -or -not (Test-Path $cfg)) {
  $url = if ($VplcUrl) { $VplcUrl } else { 'http://localhost:8088' }
  [IO.File]::WriteAllText($cfg, (@{ vplcUrl = $url } | ConvertTo-Json -Compress), $utf8)
  Write-Host "  tc-config.json vplcUrl = $url" -ForegroundColor DarkGray
} else {
  Write-Host "  tc-config.json 은 그대로 둡니다: $((Get-Content $cfg -Raw).Trim())" -ForegroundColor DarkGray
}
Write-Host "  index → $got" -ForegroundColor Green

Step '완료'
Write-Host '떠 있는 tc 에는 아직 반영되지 않았습니다.' -ForegroundColor Yellow
Write-Host '  jar 로 띄우기: ./scripts/run-backend-in-jar.ps1 (이미 떠 있으면 -Restart)' -ForegroundColor Green
Write-Host '  Eclipse: tc 프로젝트 새로 고침(F5) 뒤 다시 실행 — 실행 설정에 TC_UI_DIR 가 없어야 한다' -ForegroundColor Green
# robocopy 의 성공 코드(1~7)가 이 스크립트의 종료 코드로 새지 않게
exit 0
