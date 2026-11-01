# 가상 PLC 정지 간헐적 실패(500)와 메모리 화면 400 — 원인과 조치

> 작성일: 2026-11-01 (v0.4.0). 대상: `wow-tc-backend/vplc`, 화면 가상 PLC › 메모리 보기·제어.
> 관련: [vplc-architecture.md](vplc-architecture.md), [virtual-devices.md](virtual-devices.md)

## 1. 증상

- 가상 PLC 를 정지하면 가끔 "요청 처리 중 오류가 발생했습니다" 로 실패한다. 다시 누르면 된다.
  - 브라우저: `POST /api/v1/vplc/control/stop/{id}` → **500**
- 그 직전에 다른 오류가 하나 보인다.
  - 브라우저: `GET /api/v1/vplc/memory/{id}/area/hr?start=0&count=20` → **400**
- 사례: 메모리 보기·제어 화면에서 Modbus 가상 PLC 를 보다가 OPC UA 가상 PLC `ua-e2e` 로 바꾸고, 명령 줄의 정지를 눌렀다.

두 오류는 **원인이 서로 다르다**. 400 은 장치를 바꾼 순간, 500 은 정지한 순간에 나서 이어진 것처럼 보였을 뿐이다.

## 2. 500 — 연결 이력 저장 실패가 정지 트랜잭션까지 롤백

### 2.1 vplc 로그

```
WARN  ConnectionEvents : 연결 이력을 남기지 못했습니다: 29489e6c… 4840 DISCONNECTED — java.util.NoSuchElementException: No value present
ERROR GlobalExceptionHandler : Unhandled exception on POST /api/v1/vplc/control/stop/29489e6c…
      org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

### 2.2 흐름

1. 정지 요청이 `DefaultVirtualPlcManipulationService.stopVirtualPlc`(`@Transactional`) 안에서 OPC UA 서버를 내린다.
2. 열려 있던 세션이 닫히며 "끊김" 연결 이벤트가 **같은 스레드에서 바로** 리스너로 간다(`ClientConnectionEventDispatcher.dispatch`).
3. 리스너가 `saveConnectionClient`(`@Transactional`)를 부른다 — 정지 요청의 트랜잭션에 **합류**한다.
4. 끊긴 연결의 줄을 지우려고 `findConnectionClientEntity(...).get()` — 줄이 없어 `NoSuchElementException`.
5. 예외가 트랜잭션 경계를 지나며 트랜잭션을 **rollback-only** 로 표시한다. `ConnectionEvents` 가 예외를 잡아 정지는 계속된다(서버는 실제로 내려간다).
6. 정지 요청이 끝나 커밋하려다 `UnexpectedRollbackException` → 500. **DB 의 상태 변경(STOPPED)과 이력도 롤백**된다.

### 2.3 지울 줄이 없던 이유 — 연결 키가 IP

붙어 있는 연결 표(`connected_client`)의 키가 **(가상 PLC, 포트, 클라이언트 IP)** 였다.

- tc 는 같은 PC(127.0.0.1)에서 한 가상 PLC 에 연결을 여러 개 연다 — 커넥터의 요청 세션, 구독 세션, 연결 시험 등.
- 연결될 때는 같은 키로 덮어써 **한 줄만** 남는다. 끊길 때는 첫 연결이 그 줄을 지우고, 두 번째 연결의 끊김은 지울 줄이 없다 → 4.
- 포트의 연결 수(`connected_count`)도 +1·-1 로 세어 어긋났다.

### 2.4 간헐적인 이유, 다시 하면 되는 이유

- 정지 순간에 **같은 IP 의 연결이 둘 이상** 붙어 있을 때만 난다.
- 첫 시도에서 서버는 내려가 메모리의 상태는 STOPPED 인데 DB 는 롤백돼 RUNNING — **메모리와 DB 가 어긋난다**.
- 다시 누르면 이미 정지 상태라 닫을 연결이 없고(이벤트 없음), DB 상태만 STOPPED 로 바뀌어 성공한다.

### 2.5 범위

- OPC UA 만이 아니다. Modbus 도 한 포트(15020)에 127.0.0.1 연결이 여럿이라 같다. 요청 안에서 연결을 닫는 것 — 정지·해제·삭제·연결 끊기 — 모두 같은 길.
- MELSEC 은 끊김이 Netty 스레드에서 와서 요청 트랜잭션과 섞이지 않아 500 은 없었지만, 이력 저장 실패와 연결 수 어긋남은 같았다.

## 3. 400 — 메모리 화면이 이전 가상 PLC 의 영역으로 읽음

### 3.1 vplc 로그

```
WARN GlobalExceptionHandler : vplc 요청 거절 (errorCode=ERR_400) on GET /api/v1/vplc/memory/29489e6c…/area/hr:
     영역 메모리가 없는 가상 PLC 입니다(OPC_UA) — 태그 API 를 쓰세요
```

### 3.2 원인

메모리 화면(`VplcMemoryPage`)은 가상 PLC 를 바꾸면 영역 목록을 비우고 새 PLC 의 영역을 받는다. 그런데 **바꾼 첫 렌더**에서는

- 고른 가상 PLC 는 이미 새 것(OPC UA)인데
- 영역 목록·고른 영역은 아직 이전 것(Modbus `hr`)이라

읽기 효과가 "새 PLC + 이전 영역" 으로 한 번 실행됐다(영역을 비우는 효과와 같은 커밋에서 돈다). 다음 렌더에서는 바로잡혀 동작에는 지장이 없었지만 오류 알림이 잠깐 떴다.

## 4. 조치

| # | 조치 | 위치 |
|---|---|---|
| 1 | 연결 이력 저장이 없는 줄·지워진 가상 PLC 를 견딘다(`Optional` — 넘어간다). 포트의 연결 수는 +1·-1 대신 **붙어 있는 줄 수**로 | `DefaultVirtualPlcManagementService.saveConnectionClient`, `ConnectionClientRepository.countByVplcIdAndPort` |
| 2 | 연결 이벤트를 **부른 쪽과 떼어** 한 스레드(`vplc-connection-events`)에서 들어온 차례대로 처리 — 이력 저장이 실패해도 정지·해제·삭제 요청은 롤백되지 않는다. 리스너 예외는 로그만 | `ClientConnectionEventDispatcher` |
| 3 | 붙어 있는 연결의 키를 **(가상 PLC, 포트, 연결 아이디)** 로 — 같은 IP 의 연결 여러 개를 따로 남긴다. 연결 아이디: MELSEC Netty 채널, Modbus `modbus-<포트>-<IP>:<포트>`, OPC UA 세션 아이디. 기본 키는 ddl-auto 가 바꾸지 못하므로 **표 이름을 `VPLC_CONNECTED_CLIENT` 로 바꿔** vplc 가 새 키로 새 표를 만든다(마이그레이션 없이 동작). 옛 `connected_client` 는 쓰지 않는다 — 지우는 정리 SQL `vplc/migration/001_connected_client_channel_key.sql` | `ConnectionClientEntityId`·`ConnectionClientEntity` |
| 4 | 메모리 화면은 영역 목록을 **어느 가상 PLC 의 것인지와 함께** 둔다 — 고른 PLC 와 다르면 영역이 없는 것으로 보아 읽지 않는다 | `VplcMemoryPage` |

| 5 | 종료 때 DB 상태를 그대로 두고 포트만 돌려준다(§6) | `VirtualPlcResourceReleaseShutdownHook` |
| 6 | 기동 때 붙어 있던 연결 줄을 지우고 연결 수를 0 부터 — 지난 실행의 연결이 남지 않게(종료 때의 끊김은 DB 가 먼저 닫혀 남기지 못한다) | `VirtualPlcLoader` → `resetConnectedClients` |

### 4.1 반영 순서(로컬)

1. 새 vplc 를 띄운다 — `vplc_connected_client` 를 새 키로 만든다. tc 커넥터가 다시 붙으면 연결이 다시 쌓인다.
2. (정리, 선택) 옛 표를 지운다 — `wow-tc-backend/vplc/migration/001_connected_client_channel_key.sql` 을 vplc DB 에 실행.

처음에는 옛 표를 지우고 같은 이름으로 다시 만드는 마이그레이션을 썼는데, vplc 가 쓰는 DB 가 바뀌는 사이(`quartz-test` → `tc`, `application.yml` 의 `VPLC_DB_URL` 기본값) 실행한 DB 와 vplc 의 DB 가 어긋나 옛 키가 남았다(`Duplicate entry '127.0.0.1-15000-vplc-e2e' for key 'PRIMARY'`). 표 이름을 바꿔 DB 와 관계없이 새 키가 되게 했다.

### 4.2 시험

- e2e `test_vplc_http_methods.py` — 임시 Modbus 가상 PLC(포트 15991)에 같은 PC 에서 연결 둘 → 연결 목록 2·연결 수 2, 연결이 붙은 채 정지 → 200, 정지 뒤 연결 0, DB 상태 STOPPED.
- 메모리 화면 Playwright `vplc.spec.ts`(Modbus → OPC UA 로 바꾸는 시험 포함).

## 5. 덧붙임 — 로그의 `no certificate for provided thumbprint`

같은 로그에 `Bad_SecurityChecksFailed, no certificate for provided thumbprint` 가 되풀이된다. tc 의 보안(Basic256Sha256) OPC UA 커넥터가 vplc 를 다시 띄우기 **전**의 서버 인증서 지문으로 붙으려는 것이다 — 가상 OPC UA 서버는 시작할 때마다 인증서를 새로 만든다. 이번 일과는 따로이고, 가상 OPC UA 서버 인증서를 고정(파일에 저장)하면 없어진다(남은 일).

## 6. 함께 찾은 것 — 종료하면 모든 가상 PLC 가 STOPPED 로 남음

- 증상: vplc 를 껐다 켜면 가상 PLC 가 모두 STOPPED 로 올라와 tc 가 붙지 못한다.
- 원인: 종료 훅(`VirtualPlcResourceReleaseShutdownHook`)이 포트를 돌려주려고 **정지 서비스**를 불러, 가상 PLC 마다 DB 에 STOPPED 를 남겼다. 기동 때 `VirtualPlcLoader` 는 DB 의 마지막 상태로 되살리므로 아무것도 시작하지 않았다(예전에는 종료 때 DB 연결이 먼저 닫혀 이 기록이 실패하는 경우가 많아 드러나지 않았다).
- 조치: 종료 훅은 레지스트리의 가상 PLC 를 내리기만 한다(포트 닫기) — DB 상태는 그대로. 다시 띄우면 RUNNING·PAUSED·STOPPED 가 그대로 되살아난다.

## 7. 시험 결과

vplc 단위 20, e2e `test_vplc_http_methods` 47(같은 IP 연결 둘·붙은 채 정지 200·정지 뒤 연결 0·DB STOPPED 4개 포함)·`test_plc_vmodbus` 8·`test_plc_opcua` 30·`test_plc_points` 44·`test_plc_mc` 12, Playwright 24. vplc 를 다시 띄운 뒤 네 가상 PLC 가 RUNNING 으로 되살아남.
