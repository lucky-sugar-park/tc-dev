<#
.SYNOPSIS
  설치 폴더의 tc 를 띄우고·멈추고·재기동한다. 재기동 때 새 jar(tc\tc.jar.new)가 있으면 바꿔 끼우고, 뜨지 않으면 옛 jar 로 되돌린다.

.DESCRIPTION
  설치 폴더 구조(install.ps1 이 만든다):
    tc-ctl.ps1, env.ps1(운영자가 만든다), tc\tc.jar, tc\tc.jar.new(대기 중인 새 버전), tc\backup\, ui\, logs\, tools\ShutdownViaJmx.java
  멈출 때는 정상 종료(진행 중 PLC 요청을 마치고 PLC 연결을 닫는다, docs/operations.md §4.1)를 쓴다 — Windows 에는 SIGTERM 이 없어
  로컬 JMX 로 Spring 에 종료를 요청한다. 정해진 시간 안에 끝나지 않을 때만 강제로 끝낸다.
  tc 를 재기동하면 tc 를 부르는 MES 등의 PLC 요청이 그동안 끊긴다.

.EXAMPLE
  .\tc-ctl.ps1 status
  .\tc-ctl.ps1 restart      # tc\tc.jar.new 가 있으면 적용
#>
param(
	[Parameter(Mandatory)][ValidateSet('start', 'stop', 'restart', 'status')][string]$Action,
	[int]$StartTimeoutSeconds = 120,
	[int]$StopTimeoutSeconds = 90
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jar = Join-Path $root 'tc\tc.jar'
$newJar = Join-Path $root 'tc\tc.jar.new'
$pidFile = Join-Path $root 'tc\tc.pid'
$logDir = Join-Path $root 'logs'
$envFile = Join-Path $root 'env.ps1'

$TcEnv = @{}; $TcJavaOpts = @(); $TcAppArgs = @(); $TcServiceName = ''
if (Test-Path $envFile) { . $envFile }
$port = if ($TcEnv['TC_SERVER_PORT']) { $TcEnv['TC_SERVER_PORT'] } else { '8087' }
$useService = [bool]$TcServiceName

function Get-TcProcess {
	if ($useService) {
		$svc = Get-CimInstance Win32_Service -Filter "Name='$TcServiceName'" -ErrorAction SilentlyContinue
		if ($svc -and $svc.ProcessId) { return Get-Process -Id $svc.ProcessId -ErrorAction SilentlyContinue }
		return $null
	}
	if (-not (Test-Path $pidFile)) { return $null }
	$p = Get-Process -Id ([int](Get-Content $pidFile | Select-Object -First 1)) -ErrorAction SilentlyContinue
	if ($p -and $p.ProcessName -eq 'java') { return $p }
	return $null
}

function Get-Health {
	try {
		return (Invoke-RestMethod -Uri "http://localhost:$port/api/health" -TimeoutSec 3).status
	} catch { return $null }
}

function Start-Tc {
	if (Get-TcProcess) { Write-Host 'tc 가 이미 떠 있습니다.'; return $true }
	if (-not (Test-Path $jar)) { throw "jar 가 없습니다: $jar — install.ps1 을 먼저 실행하세요" }
	if ($useService) {
		Start-Service $TcServiceName
	} else {
		if (-not (Test-Path $envFile)) { throw "env.ps1 이 없습니다 — env.example.ps1 을 env.ps1 로 복사해 값을 채우세요" }
		$unfilled = $TcEnv.GetEnumerator() | Where-Object { "$($_.Value)" -match '^<.*>$' } | ForEach-Object { $_.Key }
		if ($unfilled) { throw "env.ps1 에 채우지 않은 값이 있습니다: $($unfilled -join ', ')" }
		New-Item -ItemType Directory -Force $logDir | Out-Null
		$out = Join-Path $logDir 'tc.out.log'; $err = Join-Path $logDir 'tc.err.log'
		foreach ($f in $out, $err) { if (Test-Path $f) { Move-Item $f "$f.1" -Force } }
		$names = @($TcEnv.Keys) + 'TC_UI_DIR'
		try {
			foreach ($k in $TcEnv.Keys) { Set-Item "Env:$k" $TcEnv[$k] }
			if (-not $TcEnv['TC_UI_DIR']) { $env:TC_UI_DIR = Join-Path $root 'ui' }
			$javaArgs = @($TcJavaOpts) + @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-jar', $jar) + @($TcAppArgs)
			$quoted = ($javaArgs | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
			# java 를 cmd 로 띄워 출력은 cmd 가 파일로 돌린다. Start-Process -RedirectStandardOutput 은 이 스크립트의 핸들을 java 에
			# 물려줘서, tc-ctl 출력을 파이프로 받는 쪽(| Tee-Object 등)이 tc 가 끝날 때까지 멈췄다
			$cmd = Start-Process -FilePath cmd.exe -ArgumentList "/c `"java $quoted 1>`"$out`" 2>`"$err`"`"" -WorkingDirectory $root `
				-WindowStyle Hidden -PassThru
		} finally {
			# 비밀값을 이 세션에 남기지 않는다
			foreach ($k in $names) { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
		}
		$javaPid = $null
		for ($i = 0; $i -lt 50 -and -not $javaPid; $i++) {
			$javaPid = (Get-CimInstance Win32_Process -Filter "ParentProcessId=$($cmd.Id) AND Name='java.exe'" | Select-Object -First 1).ProcessId
			if (-not $javaPid) { Start-Sleep -Milliseconds 200 }
		}
		if (-not $javaPid) {
			Write-Warning "java 가 뜨지 않았거나 바로 끝났습니다 — 로그: $err"
			if (Test-Path $err) { Get-Content $err -Tail 10 | ForEach-Object { Write-Host "  $_" } }
			return $false
		}
		$javaPid | Set-Content $pidFile
		Write-Host "tc 를 띄웠습니다(pid $javaPid) — 상태 확인 중"
	}
	$deadline = (Get-Date).AddSeconds($StartTimeoutSeconds)
	while ((Get-Date) -lt $deadline) {
		$h = Get-Health
		if ($h -eq 'UP' -or $h -eq 'DEGRADED') { Write-Host "tc 기동 완료 — /api/health $h (포트 $port)"; return $true }
		if (-not (Get-TcProcess)) { break }
		Start-Sleep -Seconds 2
	}
	Write-Warning "tc 가 $StartTimeoutSeconds 초 안에 뜨지 않았습니다. 로그: $logDir"
	$log = Join-Path $logDir 'tc.out.log'
	if (Test-Path $log) { Get-Content $log -Tail 15 -Encoding UTF8 | ForEach-Object { Write-Host "  $_" } }
	return $false
}

function Stop-Tc {
	$p = Get-TcProcess
	if (-not $p) { Write-Host 'tc 가 떠 있지 않습니다.'; return }
	if ($useService) {
		Stop-Service $TcServiceName
		Write-Host 'tc 서비스를 멈췄습니다.'
		return
	}
	Write-Host "tc 정상 종료 요청(pid $($p.Id)) — 진행 중 요청을 마치고 PLC 연결을 닫습니다"
	& java (Join-Path $root 'tools\ShutdownViaJmx.java') $p.Id | Out-Null
	if (-not $p.WaitForExit($StopTimeoutSeconds * 1000)) {
		Write-Warning "$StopTimeoutSeconds 초 안에 끝나지 않아 강제로 끝냅니다"
		Stop-Process -Id $p.Id -Force
		$p.WaitForExit(10000) | Out-Null
	}
	Remove-Item $pidFile -ErrorAction SilentlyContinue
	Write-Host 'tc 를 멈췄습니다.'
}

switch ($Action) {
	'status' {
		$p = Get-TcProcess
		if ($p) { Write-Host "실행 중 — pid $($p.Id), 시작 $($p.StartTime), /api/health $(Get-Health)" } else { Write-Host '멈춰 있음' }
		if (Test-Path $newJar) { Write-Host "적용 대기 중인 새 jar 가 있습니다: $newJar — 'tc-ctl.ps1 restart' 로 적용" }
		# 종료 코드 — 0 실행 중, 3 멈춤(install.ps1 이 본다)
		if ($p) { exit 0 } else { exit 3 }
	}
	'start' {
		if ((Test-Path $newJar) -and -not (Get-TcProcess)) {
			Write-Host '대기 중인 새 jar 를 띄우기 전에 바꿔 끼웁니다'
			$backup = Join-Path $root 'tc\backup'
			New-Item -ItemType Directory -Force $backup | Out-Null
			if (Test-Path $jar) { Copy-Item $jar (Join-Path $backup ("tc-{0}.jar" -f (Get-Date -Format 'yyMMdd-HHmmss'))) }
			Move-Item $newJar $jar -Force
		}
		if (-not (Start-Tc)) { exit 1 }
	}
	'stop' { Stop-Tc }
	'restart' {
		$backupJar = $null
		Stop-Tc
		if (Test-Path $newJar) {
			$backup = Join-Path $root 'tc\backup'
			New-Item -ItemType Directory -Force $backup | Out-Null
			if (Test-Path $jar) {
				$backupJar = Join-Path $backup ("tc-{0}.jar" -f (Get-Date -Format 'yyMMdd-HHmmss'))
				Copy-Item $jar $backupJar
			}
			Move-Item $newJar $jar -Force
			Write-Host "새 jar 를 적용했습니다(옛 jar: $backupJar)"
			# 백업은 최근 5개만 둔다
			Get-ChildItem $backup -Filter 'tc-*.jar' | Sort-Object Name -Descending | Select-Object -Skip 5 | Remove-Item
		}
		if (Start-Tc) { exit 0 }
		if ($backupJar) {
			Write-Warning '새 jar 로 뜨지 않아 옛 jar 로 되돌립니다'
			Stop-Tc
			Copy-Item $jar "$newJar.failed" -Force
			Copy-Item $backupJar $jar -Force
			if (Start-Tc) { Write-Warning "옛 jar 로 되돌렸습니다. 실패한 jar: $newJar.failed"; exit 2 }
		}
		exit 1
	}
}
