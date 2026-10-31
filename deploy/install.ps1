<#
.SYNOPSIS
  릴리스 꾸러미(build-release.ps1)를 설치 폴더에 넣는다. 설치할 PC 에서 꾸러미 폴더 안의 이 스크립트를 실행한다.

.DESCRIPTION
  - 화면(ui): 재기동 없이 바로 바뀐다. 새 자산을 먼저 넣고 index.html 을 바꾼 뒤 옛 자산을 지운다(보던 화면이 깨지지 않게).
    설치 폴더의 ui\tc-config.json(vplc 주소 등)은 지우거나 덮지 않는다 — -VplcUrl 을 줄 때만 쓴다.
  - 백엔드(tc.jar): tc\tc.jar.new 로 올려 둔다. -Restart 를 줄 때만 tc 를 재기동해 적용한다(tc-ctl.ps1 restart — 정상 종료,
    뜨지 않으면 옛 jar 로 되돌림). tc 가 멈춰 있으면 바로 바꿔 끼운다. tc 를 재기동하면 MES 등의 PLC 요청이 그동안 끊긴다.
  - DB 스크립트(migration)는 돌리지 않는다 — 설치 폴더에 복사만 하고 새 파일을 알린다(docs/operations.md §3.1).
  - env.ps1(비밀값)은 만들거나 덮지 않는다.

.EXAMPLE
  .\install.ps1 -InstallDir C:\wow-tc                  # 화면은 바로, 백엔드는 대기
  .\install.ps1 -InstallDir C:\wow-tc -Restart         # 백엔드도 재기동해 적용
  .\install.ps1 -InstallDir C:\wow-tc -Part Ui         # 화면만
  .\install.ps1 -InstallDir C:\wow-tc -VplcUrl http://localhost:8088   # 시험 PC — 가상 PLC 메뉴를 켠다
#>
param(
	[string]$InstallDir = 'C:\wow-tc',
	[ValidateSet('All', 'Ui', 'Backend')][string]$Part = 'All',
	[switch]$Restart,
	[string]$VplcUrl
)
$ErrorActionPreference = 'Stop'
$pkg = $PSScriptRoot
Write-Host "꾸러미: $pkg"
Get-Content (Join-Path $pkg 'VERSION.txt') | ForEach-Object { Write-Host "  $_" }
New-Item -ItemType Directory -Force $InstallDir, (Join-Path $InstallDir 'tc'), (Join-Path $InstallDir 'tools') | Out-Null

# 제어 스크립트·도구·예시는 늘 새것으로(env.ps1 은 건드리지 않는다)
Copy-Item (Join-Path $pkg 'tc-ctl.ps1'), (Join-Path $pkg 'env.example.ps1') $InstallDir -Force
Copy-Item (Join-Path $pkg 'tools\*') (Join-Path $InstallDir 'tools') -Force
Copy-Item (Join-Path $pkg 'VERSION.txt') (Join-Path $InstallDir 'VERSION.txt') -Force

function Invoke-Robocopy([string[]]$RoboArgs) {
	& robocopy @RoboArgs /NJH /NJS /NFL /NDL /NP | Out-Null
	if ($LASTEXITCODE -ge 8) { throw "robocopy 실패($LASTEXITCODE): $($RoboArgs -join ' ')" }
}

$uiSrc = Join-Path $pkg 'ui'
if ($Part -ne 'Backend' -and (Test-Path $uiSrc)) {
	$ui = Join-Path $InstallDir 'ui'
	New-Item -ItemType Directory -Force $ui | Out-Null
	# 1) 새 자산(이름에 해시) 먼저 2) index.html 등 맨 위 파일 3) 옛 자산 정리 — tc-config.json 은 제외(설치한 곳의 값)
	if (Test-Path (Join-Path $uiSrc 'assets')) { Invoke-Robocopy @((Join-Path $uiSrc 'assets'), (Join-Path $ui 'assets'), '/E') }
	Invoke-Robocopy @($uiSrc, $ui, '/LEV:1', '/XF', 'tc-config.json')
	Invoke-Robocopy @($uiSrc, $ui, '/MIR', '/XF', 'tc-config.json')
	$cfg = Join-Path $ui 'tc-config.json'
	$utf8 = New-Object System.Text.UTF8Encoding($false)   # BOM 없이(브라우저가 JSON 으로 읽는다)
	if ($VplcUrl) {
		[IO.File]::WriteAllText($cfg, (@{ vplcUrl = $VplcUrl } | ConvertTo-Json), $utf8)
	} elseif (-not (Test-Path $cfg)) {
		[IO.File]::WriteAllText($cfg, '{}', $utf8)   # 운영 기본 — 가상 PLC 메뉴 숨김
	}
	Write-Host "화면을 바꿨습니다: $ui (재기동 없음 — 브라우저 새로 고침으로 보인다)"
}

$jarSrc = Join-Path $pkg 'tc\tc.jar'
if ($Part -ne 'Ui' -and (Test-Path $jarSrc)) {
	$mig = Join-Path $pkg 'migration'
	if (Test-Path $mig) {
		$migDst = Join-Path $InstallDir 'migration'
		$known = if (Test-Path $migDst) { @(Get-ChildItem $migDst -Filter *.sql | ForEach-Object Name) } else { @() }
		$new = @(Get-ChildItem $mig -Filter *.sql | Where-Object { $known -notcontains $_.Name } | ForEach-Object Name)
		Invoke-Robocopy @($mig, $migDst, '/E')
		if ($new.Count -and $known.Count) {
			Write-Warning "새 DB 스크립트가 있습니다 — tc 를 재기동하기 전에 번호 순서대로 돌리세요(docs/operations.md §3.1): $($new -join ', ')"
		}
	}
	Copy-Item $jarSrc (Join-Path $InstallDir 'tc\tc.jar.new') -Force
	$ctl = Join-Path $InstallDir 'tc-ctl.ps1'
	& powershell -NoProfile -ExecutionPolicy Bypass -File $ctl status | Out-Null
	$running = $LASTEXITCODE -eq 0
	if ($Restart) {
		& powershell -NoProfile -ExecutionPolicy Bypass -File $ctl restart
		if ($LASTEXITCODE -ne 0) { throw "재기동 실패($LASTEXITCODE) — 로그: $(Join-Path $InstallDir 'logs')" }
	} elseif (-not $running) {
		Move-Item (Join-Path $InstallDir 'tc\tc.jar.new') (Join-Path $InstallDir 'tc\tc.jar') -Force
		Write-Host "tc.jar 를 넣었습니다(tc 는 멈춰 있음). 띄우기: $ctl start   (처음이면 env.example.ps1 을 env.ps1 로 복사해 채운 뒤)"
	} else {
		Write-Host "새 tc.jar 를 올려 두었습니다(tc\tc.jar.new). tc 가 실행 중이라 바꾸지 않았습니다."
		Write-Host "적용하려면 재기동이 필요합니다 — MES 등의 PLC 요청이 잠시 끊깁니다:  $ctl restart"
	}
}
