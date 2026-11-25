<#
.SYNOPSIS
  WOW-TC 가상 PLC(vplc)를 멈춘다(정상 종료).

.DESCRIPTION
  run-vplc.ps1 -Stop 을 부른다 — 로컬 JMX 로 정상 종료를 요청하고, 제한 시간을 넘거나 JMX 가 꺼져 있으면 강제로 끝낸다.
  떠 있지 않으면 그냥 지나간다. 스크립트 없이 띄운 vplc 도 포트를 쥔 프로세스가 vplc jar 면 멈춘다.
  vplc 를 멈추면 붙어 있던 tc 커넥터가 끊기고, 다시 띄울 때까지 다시 연결을 시도한다.
  종료 코드: 0 = 멈춤(또는 떠 있지 않음), 그 밖 = 실패.

.PARAMETER Port
  서버 포트 (기본은 run-vplc.ps1 의 기본값).

.PARAMETER RunDir
  실행 폴더 (기본은 run-vplc.ps1 의 기본값).

.PARAMETER StopTimeoutSeconds
  정상 종료를 기다리는 시간 (기본은 run-vplc.ps1 의 기본값).

.EXAMPLE
  ./scripts/stop-vplc.ps1
#>
param(
  [int]$Port,
  [string]$RunDir,
  [int]$StopTimeoutSeconds
)
$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'run-vplc.ps1') -Stop @PSBoundParameters
exit $LASTEXITCODE
