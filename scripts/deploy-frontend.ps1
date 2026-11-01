<#
.SYNOPSIS
  WOW-TC 화면(wow-tc-frontend) 빌드 → 백엔드(tc)가 서빙하는 폴더로 배포.

.DESCRIPTION
  순서:
    1) (옵션) npm install (-Install)
    2) npm run build — tsc 검사 + vite build → wow-tc-frontend\dist
    3) dist 를 화면 폴더(기본 run\ui — run-backend.ps1 이 TC_UI_DIR 로 넘긴다)에 맞춘다. **tc 재기동 없음.**
         새 자산(이름에 해시)을 먼저 넣고 → index.html 등 맨 위 파일 → 옛 자산 정리. 그래서 보던 화면이 깨지지 않는다.
         복사 뒤 index.html 이 dist 와 같은지 확인한다.
    4) tc-config.json(배포한 곳의 값 — 가상 장비 주소)은 덮지 않는다. 없으면 만든다(기본 vplcUrl http://localhost:8088,
         vplcModbusUrl http://localhost:8091, vplcOpcuaUrl http://localhost:8092). 주소 인자를 주면 그 칸만 바꾼다.
         dist 의 tc-config.json(개발 서버용 /vplc-api)은 복사하지 않는다.

  운영 tc 처럼 다른 폴더를 서빙한다면 -UiDir 로 그 폴더(tc 의 TC_UI_DIR)를 준다. 가상 PLC 가 없는 곳은 -NoVplc.
  화면 문구(다국어)는 tc 기동 때 씨앗으로 들어가므로, 문구 씨앗을 바꿨다면 백엔드도 배포·재기동한다.

.PARAMETER Install
  빌드 전에 npm install 실행 (의존성 변경 시).

.PARAMETER SkipDeploy
  빌드만 하고 복사하지 않는다.

.PARAMETER UiDir
  배포할 화면 폴더 (기본 레포 루트 run\ui).

.PARAMETER VplcUrl
  tc-config.json 의 가상 PLC 주소를 이 값으로 쓴다(주면 기존 값을 덮는다). vplc 의 CORS 허용 출처에 tc 화면 주소가 있어야 한다.

.PARAMETER VplcModbusUrl
  tc-config.json 의 가상 Modbus 장비(vplc-modbus) 주소. 빈 문자열이면 그 칸을 지운다(목록·메모리 화면에서 빠진다).

.PARAMETER VplcOpcuaUrl
  tc-config.json 의 가상 OPC UA 서버(vplc-opcua) 주소. 빈 문자열이면 그 칸을 지운다.

.PARAMETER NoVplc
  tc-config.json 을 {} 로 쓴다 — 가상 PLC 메뉴를 숨긴다(운영).

.EXAMPLE
  ./scripts/deploy-frontend.ps1
  빌드 → run\ui 배포(떠 있는 tc 에 바로 반영).

.EXAMPLE
  ./scripts/deploy-frontend.ps1 -Install
  의존성이 바뀐 경우 npm install 부터.

.EXAMPLE
  ./scripts/deploy-frontend.ps1 -UiDir D:\wow-tc\ui -NoVplc
  다른 tc 가 서빙하는 폴더로, 가상 PLC 메뉴 없이.
#>
param(
  [switch]$Install,
  [switch]$SkipDeploy,
  [string]$UiDir,
  [string]$VplcUrl,
  [string]$VplcModbusUrl,
  [string]$VplcOpcuaUrl,
  [switch]$NoVplc
)
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$fe   = Join-Path $repo 'wow-tc-frontend'
$dist = Join-Path $fe 'dist'
if (-not $UiDir) { $UiDir = Join-Path $repo 'run\ui' }
function Step([string]$m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# robocopy 종료 코드는 8 미만이 성공(1=복사함, 2=여분 정리, 3=둘 다 …). Git Bash 의 robocopy 는 조용히 아무것도 안 하므로 PowerShell 에서 돈다
function Invoke-Robocopy([string]$from, [string]$to, [string[]]$extra) {
  robocopy $from $to /NFL /NDL /NJH /NJS /NP @extra | Out-Null
  if ($LASTEXITCODE -ge 8) { throw "robocopy 실패(rc=$LASTEXITCODE): $from → $to" }
}

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

if ($SkipDeploy) {
  Step '완료 — 배포 건너뜀 (-SkipDeploy)'
  Write-Host "빌드 산출물: $dist" -ForegroundColor Yellow
  exit 0
}

Step "deploy → $UiDir"
New-Item -ItemType Directory -Force $UiDir | Out-Null
# 1) 새 자산 먼저(옛 것은 아직 남긴다) 2) 맨 위 파일(index.html …) 3) 옛 자산 정리 — tc-config.json 은 어느 단계에서도 건드리지 않는다
if (Test-Path (Join-Path $dist 'assets')) { Invoke-Robocopy (Join-Path $dist 'assets') (Join-Path $UiDir 'assets') @('/E') }
Invoke-Robocopy $dist $UiDir @('/LEV:1', '/XF', 'tc-config.json')
Invoke-Robocopy $dist $UiDir @('/MIR', '/XF', 'tc-config.json')

$want = Get-IndexBundle (Join-Path $dist 'index.html')
$got  = Get-IndexBundle (Join-Path $UiDir 'index.html')
if ($want -ne $got) { throw "index.html 이 dist 와 다릅니다($UiDir): $got ≠ $want" }

$cfg = Join-Path $UiDir 'tc-config.json'
$utf8 = New-Object System.Text.UTF8Encoding($false)   # BOM 없이(브라우저가 JSON 으로 읽는다)
if ($NoVplc) {
  [IO.File]::WriteAllText($cfg, '{}', $utf8)
  Write-Host '  tc-config.json = {} (가상 PLC 메뉴 숨김)' -ForegroundColor DarkGray
} elseif (-not (Test-Path $cfg) -or $PSBoundParameters.ContainsKey('VplcUrl') -or $PSBoundParameters.ContainsKey('VplcModbusUrl') -or $PSBoundParameters.ContainsKey('VplcOpcuaUrl')) {
  # 있던 값에 준 칸만 덮는다(없던 파일은 기본값 셋)
  $values = [ordered]@{}
  if (Test-Path $cfg) {
    (Get-Content $cfg -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $values[$_.Name] = $_.Value }
  } else {
    $values.vplcUrl = 'http://localhost:8088'; $values.vplcModbusUrl = 'http://localhost:8091'; $values.vplcOpcuaUrl = 'http://localhost:8092'
  }
  foreach ($k in 'VplcUrl', 'VplcModbusUrl', 'VplcOpcuaUrl') {
    if ($PSBoundParameters.ContainsKey($k)) {
      $key = $k.Substring(0, 1).ToLower() + $k.Substring(1)
      if ($PSBoundParameters[$k]) { $values[$key] = $PSBoundParameters[$k] } else { $values.Remove($key) }
    }
  }
  [IO.File]::WriteAllText($cfg, ($values | ConvertTo-Json -Compress), $utf8)
  Write-Host "  tc-config.json = $((Get-Content $cfg -Raw).Trim())" -ForegroundColor DarkGray
} else {
  Write-Host "  tc-config.json 은 그대로 둡니다: $((Get-Content $cfg -Raw).Trim())" -ForegroundColor DarkGray
}
Write-Host "  index → $got" -ForegroundColor Green

Step '완료'
Write-Host '재기동 없이 반영됩니다 — 브라우저 새로 고침으로 확인하세요.' -ForegroundColor Green
if ($UiDir -eq (Join-Path $repo 'run\ui')) { Write-Host 'tc 가 떠 있지 않다면: ./scripts/run-backend.ps1' -ForegroundColor Green }
# robocopy 의 성공 코드(1~7)가 이 스크립트의 종료 코드로 새지 않게
exit 0
