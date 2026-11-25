<#
.SYNOPSIS
  WOW-TC 백엔드(tc)와 가상 PLC(vplc)를 함께 멈춘다(정상 종료).

.DESCRIPTION
  run-backend.ps1 -Stop → run-vplc.ps1 -Stop 차례로 부른다. 멈추는 방법은 두 스크립트 그대로다(로컬 JMX 로 정상 종료, 제한 시간을 넘으면 강제 종료).
  tc 를 먼저 멈춘다 — vplc 를 먼저 멈추면 그 사이 tc 커넥터가 끊김 이력을 남기고 다시 연결을 시도한다.
  하나를 멈추지 못해도 나머지는 멈춘다. 떠 있지 않은 것은 그냥 지나간다.
  tc 를 멈추면 tc 를 부르는 MES 등의 PLC 요청이 다시 띄울 때까지 실패한다.
  스크립트 없이 띄운 tc 는 멈추지 않는다 — tc 는 pid 파일(run\tc\tc.pid)로 찾는다.
  포트·실행 폴더를 기본값과 다르게 띄웠으면 두 스크립트를 -Stop 과 그 옵션으로 따로 부른다.
  종료 코드: 0 = 모두 멈춤(또는 떠 있지 않음), 1 = 멈추지 못한 것이 있음.

.EXAMPLE
  ./scripts/stop-all.ps1
#>
$ErrorActionPreference = 'Stop'

$failed = @()
foreach ($app in @(@{ Name = 'tc'; Script = 'run-backend.ps1' }, @{ Name = 'vplc'; Script = 'run-vplc.ps1' })) {
  try {
    & (Join-Path $PSScriptRoot $app.Script) -Stop
    if ($LASTEXITCODE -ne 0) { throw "종료 코드 $LASTEXITCODE" }
  } catch {
    Write-Host "$($app.Name) 를 멈추지 못했습니다: $($_.Exception.Message)" -ForegroundColor Red
    $failed += $app.Name
  }
}

if ($failed.Count -gt 0) {
  Write-Host "멈추지 못함: $($failed -join ', ')" -ForegroundColor Red
  exit 1
}
Write-Host '모두 멈췄습니다.' -ForegroundColor Green
exit 0
