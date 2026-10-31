# 빌드 / 배포 / 구동 스크립트

Windows 기준. 레포 루트(`D:\Source\claude-code\Projects\tc-dev`)에서 실행한다. mes-dev 의 `scripts/` 와 같은 구성이다.

| 스크립트 | 역할 |
|----------|------|
| `deploy-frontend.ps1` | 화면(`wow-tc-frontend`) 빌드 → 백엔드(tc)가 서빙하는 폴더(`run\ui`)로 배포. **재기동 없음** |
| `deploy-backend.ps1` | 백엔드(tc) 빌드(+테스트) → Nexus 배포(`com.wow.be:wow-tc:<버전>`) → Nexus 에서 받아 **재기동** |
| `run-backend.ps1` | 백엔드(tc) 구동 — 띄우기·멈추기·재기동·상태 |

실행 폴더는 레포 루트의 `run\`(git 에 넣지 않는다):

```
run\tc\tc.jar        띄우는 jar        (tc.jar.new = 적용 대기, tc.jar.prev = 바로 전 jar)
run\tc\tc.pid
run\ui\              화면 — tc 가 TC_UI_DIR 로 서빙(tc-config.json 은 이곳의 값, 배포 때 덮지 않는다)
run\logs\            tc.out.log / tc.err.log (직전 것은 .1)
run\local\           로컬 비밀값(jwt-secret.txt, secret-key.txt) — 처음 기동 때 만든다
```

## 사전 조건

- 로컬 인프라: **MariaDB 3306** · **Redis 6379** · **Nexus 8081**(maven·npm group)
- JDK 25(`java`), Node.js(`npm`)
- Nexus 업로드 자격증명: 전역 `~/.gradle/gradle.properties` 의 `nexusUsername` / `nexusPassword` (mes-dev 와 같은 값, 리포지토리에 두지 않는다)
- DB 비밀번호: `wow-tc-backend\tc\src\main\resources\application-local.yml`(git 제외) — `run-backend.ps1` 이 이 파일을 읽게 해서 띄운다. 없으면 환경 변수 `TC_DB_PASSWORD`
- 가상 PLC 화면을 쓰려면 vplc(8088)가 떠 있고, vplc 의 CORS 허용 출처에 tc 화면 주소가 있어야 한다(기본 `http://localhost:3000,5180,8087`)

## 실행 방법 — PowerShell

```powershell
cd D:\Source\claude-code\Projects\tc-dev

.\scripts\deploy-frontend.ps1        # 화면 빌드 → run\ui 배포
.\scripts\deploy-backend.ps1         # 백엔드 빌드 → Nexus → 재기동
.\scripts\run-backend.ps1            # 백엔드 띄우기
```

`.\` 와 `./` 둘 다 된다.

### 보안 오류(`PSSecurityException` / `UnauthorizedAccess`)가 뜨는 경우

PowerShell 기본 실행 정책이 `Restricted` 면 아래처럼 막힌다:

```
... 이 시스템에서 스크립트를 실행할 수 없으므로 ...
+ CategoryInfo          : 보안 오류: (:) [], PSSecurityException
+ FullyQualifiedErrorId : UnauthorizedAccess
```

**① 현재 사용자만 한 번 풀기 (관리자 권한 필요 없음, 권장)**

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned   # 확인 메시지에 Y
.\scripts\run-backend.ps1
```

`RemoteSigned` = 내가 만든 로컬 스크립트는 그냥 실행하고, 인터넷에서 받은 스크립트만 서명을 요구한다.

**② 정책을 바꾸기 싫으면 — 이 창에서만 우회**

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\run-backend.ps1
```

**③ 그래도 막히면 (인터넷·압축 해제로 받은 파일의 차단 표시)**

```powershell
Unblock-File .\scripts\*.ps1
```

현재 정책 확인: `Get-ExecutionPolicy -List`

## 실행 방법 — cmd(명령 프롬프트)

`.ps1` 은 PowerShell 스크립트라 cmd 에서 `scripts\run-backend.ps1` 로는 실행되지 않는다(메모장으로 열리거나 아무 일도 없다). `powershell` 을 불러서 실행한다 — 실행 정책을 바꾸지 않아도 된다:

```cmd
cd /d D:\Source\claude-code\Projects\tc-dev

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy-frontend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy-backend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-backend.ps1
```

옵션은 뒤에 그대로 붙인다:

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy-backend.ps1 -SkipTests
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-backend.ps1 -Restart
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy-frontend.ps1 -UiDir D:\wow-tc\ui -NoVplc
```

종료 코드는 `echo %ERRORLEVEL%` 로 본다(0 성공, 1 실패, 2 새 jar 로 뜨지 않아 전 jar 로 되돌림, `run-backend.ps1 -Status` 는 0 실행 중·3 멈춤).

## 1) 화면 빌드 → 배포 (`deploy-frontend.ps1`)

```powershell
.\scripts\deploy-frontend.ps1                    # 빌드 → run\ui (떠 있는 tc 에 바로 반영)
.\scripts\deploy-frontend.ps1 -Install           # 의존성이 바뀐 경우 npm install 부터
.\scripts\deploy-frontend.ps1 -SkipDeploy        # 빌드만
.\scripts\deploy-frontend.ps1 -UiDir D:\wow-tc\ui -NoVplc       # 다른 tc 가 서빙하는 폴더로, 가상 PLC 메뉴 없이
.\scripts\deploy-frontend.ps1 -VplcUrl http://10.0.0.5:8088      # 가상 PLC 주소를 바꿔 쓴다
```

- 순서: `npm run build`(tsc 검사 + vite) → 새 자산 → `index.html` 등 → 옛 자산 정리. 보던 화면이 깨지지 않고, 복사 뒤 `index.html` 이 dist 와 같은지 확인한다.
- `tc-config.json` 은 덮지 않는다(없을 때만 `vplcUrl: http://localhost:8088` 으로 만든다). `-VplcUrl` 이나 `-NoVplc` 를 줄 때만 쓴다.
- 브라우저 캐시로 옛 화면이 보이면 새로 고침.
- 화면 문구(다국어) 씨앗(`wow-tc-backend\tc\src\main\resources\ui-seed`)은 tc 기동 때 들어가므로, 문구 씨앗을 바꿨다면 백엔드도 배포한다.

## 2) 백엔드 빌드 → Nexus 배포 → 재기동 (`deploy-backend.ps1`)

```powershell
.\scripts\deploy-backend.ps1                      # 빌드(+테스트) → Nexus publish → 받아서 재기동
.\scripts\deploy-backend.ps1 -SkipTests           # 테스트 없이
.\scripts\deploy-backend.ps1 -SkipRestart         # 새 jar 는 run\tc\tc.jar.new 로 대기만(재기동은 나중에)
.\scripts\deploy-backend.ps1 -SkipPublish         # Nexus 없이 빌드 → 재기동
.\scripts\deploy-backend.ps1 -Clean               # clean 부터
```

- 순서: `gradlew :tc:build`(단위 테스트 — 인프라 없이 돈다) → `:tc:publish` → Nexus(group)에서 `wow-tc-<버전>.jar` 를 받아 빌드한 것과 같은지(SHA-256) 확인 → `run\tc\tc.jar.new` → `run-backend.ps1 -Restart`.
- 올리는 곳: 버전이 SNAPSHOT 이면 `inzi-mes-platform-maven-private-snapshot`, 아니면 `inzi-mes-platform-maven-private`. 버전은 `wow-tc-backend\build.gradle` 의 `version`(회차 브랜치 버전).
- **재기동하면 tc 를 부르는 MES 등의 PLC 요청이 그동안 끊긴다.** 재기동은 정상 종료(진행 중 요청을 마치고 PLC 연결을 닫음) → 새 jar 적용 → `/api/health` 확인이고, 2분 안에 뜨지 않으면 **전 jar 로 되돌린다**(종료 코드 2, 실패한 jar 는 `run\tc\tc.jar.failed`).
- DB 스크립트(`wow-tc-backend\migration`)는 돌리지 않는다 — 새 스크립트가 있으면 재기동 전에 번호 순서대로 돌린다(docs/operations.md §3.1).

## 3) 백엔드 구동 (`run-backend.ps1`)

```powershell
.\scripts\run-backend.ps1               # 백그라운드로 8087 에 띄우고 기동 확인
.\scripts\run-backend.ps1 -Status       # 실행 여부·health·대기 중인 jar
.\scripts\run-backend.ps1 -Restart      # 재기동(대기 중인 새 jar 적용, 실패하면 전 jar 로)
.\scripts\run-backend.ps1 -Stop         # 정상 종료
.\scripts\run-backend.ps1 -Foreground   # 이 창에서 띄우기(로그가 창에 나온다, Ctrl+C 로 종료)
.\scripts\run-backend.ps1 -Port 8097    # 다른 포트
```

- 띄울 jar 가 없으면(처음) 빌드 산출물(`wow-tc-backend\tc\build\libs`)을 복사해 쓴다.
- `TC_JWT_SECRET`·`TC_SECRET_KEY` 는 환경 변수가 있으면 그것을, 없으면 `run\local\` 의 값을 쓴다(처음에 만든다). 그 밖의 설정은 이 창의 환경 변수(`TC_*`, docs/operations.md §2.1)로 준다. 넘긴 비밀값은 이 창에 남기지 않는다.
- Windows 에는 SIGTERM 이 없어, 멈출 때는 로컬 JMX 로 Spring 에 종료를 요청한다(`wow-tc-backend\e2e\ShutdownViaJmx.java`). 90초 안에 끝나지 않을 때만 강제로 끝낸다.
- 화면: http://localhost:8087 (tc 가 `run\ui` 를 서빙). 화면 개발은 `wow-tc-frontend` 에서 `npm run dev`(5180).

## 일반 흐름

```powershell
.\scripts\deploy-frontend.ps1     # 화면만 바뀌었을 때 — 재기동 없음
.\scripts\deploy-backend.ps1      # 백엔드가 바뀌었을 때 — 빌드 → Nexus → 재기동
```

처음에는 `deploy-frontend.ps1` → `deploy-backend.ps1`(또는 `run-backend.ps1`) 순서로.
