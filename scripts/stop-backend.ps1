<#
.SYNOPSIS
  WOW-TC 백엔드(tc)를 멈춘다(정상 종료).

.DESCRIPTION
  run-backend.ps1 -Stop 을 부른다 — 로컬 JMX 로 정상 종료를 요청하고, 제한 시간을 넘으면 강제로 끝낸다.
  떠 있지 않으면 그냥 지나간다.
  tc 를 멈추면 tc 를 부르는 MES 등의 PLC 요청이 다시 띄울 때까지 실패한다.
  스크립트 없이 띄운 tc 는 멈추지 않는다 — tc 는 실행 폴더의 pid 파일로 찾는다.
  종료 코드: 0 = 멈춤(또는 떠 있지 않음), 그 밖 = 실패.

.PARAMETER Port
  서버 포트 (기본은 run-backend.ps1 의 기본값).

.PARAMETER RunDir
  실행 폴더 (기본은 run-backend.ps1 의 기본값).

.PARAMETER StopTimeoutSeconds
  정상 종료를 기다리는 시간 (기본은 run-backend.ps1 의 기본값).

.EXAMPLE
  ./scripts/stop-backend.ps1
#>
param(
  [int]$Port,
  [string]$RunDir,
  [int]$StopTimeoutSeconds
)
$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'run-backend.ps1') -Stop @PSBoundParameters
exit $LASTEXITCODE
