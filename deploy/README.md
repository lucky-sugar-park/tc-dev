# tc 배포 스크립트

개발 PC 에서 **꾸러미**를 만들고, 설치할 PC 에서 꾸러미 안의 `install.ps1` 을 실행한다. Windows PowerShell 5.1 이상, 설치할 PC 에 JDK 25(`java`)가 있어야 한다.
자세한 운영 절차는 [docs/operations.md](../docs/operations.md) §3.3.

| 파일 | 어디서 | 하는 일 |
|---|---|---|
| `build-release.ps1` | 개발 PC | tc jar(`gradlew :tc:bootJar`)·화면(`npm run build`)을 빌드해 `deploy\out\wow-tc-<버전>-<yyMMdd-HHmm>\` 폴더와 zip 으로 묶는다. `-SkipBackend`·`-SkipFrontend` |
| `install.ps1` | 설치할 PC(꾸러미 안) | 설치 폴더(기본 `C:\wow-tc`)에 넣는다. 화면은 바로 바뀐다(재기동 없음). 백엔드는 `tc\tc.jar.new` 로 올려 두고 **`-Restart` 를 줄 때만** 재기동해 적용한다. `-Part Ui|Backend`, `-VplcUrl` |
| `tc-ctl.ps1` | 설치 폴더 | `start`·`stop`·`restart`·`status`. 정상 종료(JMX), 재기동 때 대기 중인 새 jar 적용, 뜨지 않으면 옛 jar 로 되돌림(종료 코드 2) |
| `env.example.ps1` | 설치 폴더 | 실행 환경(DB·JWT 키·포트 …) 예시 — `env.ps1` 로 복사해 채운다. **env.ps1 은 설치한 PC 에만** |

```powershell
# 개발 PC
.\deploy\build-release.ps1

# 설치할 PC — 꾸러미를 풀고 그 안에서
.\install.ps1 -InstallDir C:\wow-tc                 # 처음: 넣기만. env.ps1 을 채운 뒤 C:\wow-tc\tc-ctl.ps1 start
.\install.ps1 -InstallDir C:\wow-tc -Part Ui        # 화면만(재기동 없음)
.\install.ps1 -InstallDir C:\wow-tc -Restart        # 백엔드도 — tc 를 재기동한다(MES 등의 PLC 요청이 잠시 끊긴다)
```

설치 폴더: `tc-ctl.ps1`, `env.ps1`, `tc\tc.jar`(`tc.jar.new` 대기, `backup\` 최근 5개), `ui\`(`tc-config.json` 은 설치한 곳의 값 — 덮지 않는다), `logs\`, `migration\`, `tools\`, `VERSION.txt`.
