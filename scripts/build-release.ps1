<#
.SYNOPSIS
  tc 릴리스 꾸러미를 만든다 — 백엔드 jar + 화면(dist) + DB 스크립트 + 설치·제어 스크립트를 한 폴더와 zip 으로.

.DESCRIPTION
  개발 PC 에서 돌린다. 만든 꾸러미(deploy\out\wow-tc-<버전>-<yyMMdd-HHmm>.zip)를 설치할 PC 에 풀고 그 안의 install.ps1 을 실행한다.
  비밀값은 꾸러미에 넣지 않는다 — 설치 폴더의 env.ps1(설치할 PC 에서 운영자가 만든다).

.EXAMPLE
  .\deploy\build-release.ps1                 # 백엔드·화면 모두
  .\deploy\build-release.ps1 -SkipBackend    # 화면만 바뀐 배포
#>
param(
	[switch]$SkipBackend,
	[switch]$SkipFrontend,
	[string]$OutDir
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $repo 'wow-tc-backend'
$frontend = Join-Path $repo 'wow-tc-frontend'
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot 'out' }

$version = ([regex]::Match((Get-Content (Join-Path $backend 'build.gradle') -Raw), "version\s*=\s*'([^']+)'")).Groups[1].Value
$stamp = Get-Date -Format 'yyMMdd-HHmm'
$commit = (git -C $repo rev-parse --short HEAD).Trim()
$dirty = (git -C $repo status --porcelain -- wow-tc-backend wow-tc-frontend deploy) -ne $null
if ($dirty) { Write-Warning '커밋하지 않은 변경이 꾸러미에 들어갑니다(VERSION.txt 에 dirty 로 남김).' }

$name = "wow-tc-$version-$stamp"
$pkg = Join-Path $OutDir $name
New-Item -ItemType Directory -Force $pkg | Out-Null

if (-not $SkipBackend) {
	Write-Host '== 백엔드 jar'
	& (Join-Path $backend 'gradlew.bat') -p $backend ':tc:bootJar' -q
	if ($LASTEXITCODE -ne 0) { throw "gradle 빌드 실패($LASTEXITCODE)" }
	New-Item -ItemType Directory -Force (Join-Path $pkg 'tc') | Out-Null
	Copy-Item (Join-Path $backend "tc\build\libs\tc-$version.jar") (Join-Path $pkg 'tc\tc.jar')
	Copy-Item (Join-Path $backend 'migration') (Join-Path $pkg 'migration') -Recurse
}

if (-not $SkipFrontend) {
	Write-Host '== 화면 빌드'
	Push-Location $frontend
	try {
		npm run build
		if ($LASTEXITCODE -ne 0) { throw "화면 빌드 실패($LASTEXITCODE)" }
	} finally { Pop-Location }
	Copy-Item (Join-Path $frontend 'dist') (Join-Path $pkg 'ui') -Recurse
	# tc-config.json 은 설치한 곳의 값(vplc 주소 등)을 쓴다 — 개발용 값(/vplc-api)을 싣지 않는다
	Remove-Item (Join-Path $pkg 'ui\tc-config.json') -ErrorAction SilentlyContinue
}

Copy-Item (Join-Path $PSScriptRoot 'install.ps1'), (Join-Path $PSScriptRoot 'tc-ctl.ps1'), (Join-Path $PSScriptRoot 'env.example.ps1') $pkg
New-Item -ItemType Directory -Force (Join-Path $pkg 'tools') | Out-Null
Copy-Item (Join-Path $backend 'e2e\ShutdownViaJmx.java') (Join-Path $pkg 'tools')

$parts = @()
if (-not $SkipBackend) { $parts += 'backend' }
if (-not $SkipFrontend) { $parts += 'ui' }
@"
version=$version
built=$stamp
commit=$commit$(if ($dirty) { ' (dirty)' })
parts=$($parts -join ',')
"@ | Set-Content (Join-Path $pkg 'VERSION.txt') -Encoding UTF8

$zip = "$pkg.zip"
Compress-Archive -Path (Join-Path $pkg '*') -DestinationPath $zip -Force
Write-Host ''
Write-Host "꾸러미: $pkg"
Write-Host "zip   : $zip"
Write-Host "설치  : 설치할 PC 에서 풀고  .\install.ps1 -InstallDir C:\wow-tc   (백엔드를 바꾸면 -Restart 를 붙일 때만 재기동)"
