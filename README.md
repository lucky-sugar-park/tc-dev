# wow-tc

설비 데이터 수집 및 제어 시스템

빌드·배포·구동 스크립트: [scripts/README.md](scripts/README.md) (화면 배포, 백엔드 빌드 → Nexus → 재기동, 백엔드 구동, 가상 PLC 구동)

| 하고 싶은 일 | 명령(레포 루트, PowerShell) |
|---|---|
| 화면 배포(재기동 없음) | `.\scripts\deploy-frontend.ps1` |
| 백엔드(tc) 빌드 → Nexus → 재기동 | `.\scripts\deploy-backend.ps1` |
| 백엔드(tc) 상태·재기동 | `.\scripts\run-backend.ps1 -Status` · `-Restart` |
| 가상 PLC(vplc) 빌드 → 재기동 | `.\scripts\run-vplc.ps1 -Restart -Build` |
| 가상 PLC(vplc) 상태·재기동 | `.\scripts\run-vplc.ps1 -Status` · `-Restart` |

tc 를 재기동하면 tc 를 부르는 MES 등의 PLC 요청이, vplc 를 재기동하면 vplc 에 붙은 tc 커넥터 연결이 그동안 끊긴다(다시 붙는다).
