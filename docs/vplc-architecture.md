# vplc 구조 — 가상 PLC 추상화와 종류별 구현

> 작성일: 2026-11-01 (v0.4.0). 대상: `wow-tc-backend/vplc`.
> 관련: [virtual-devices.md](virtual-devices.md)(설계 결정 V1~V10·REST), [vplc-guide.md](vplc-guide.md)(종류별 사용 안내)

vplc 는 MELSEC MC·Modbus TCP·OPC UA 가상 PLC 를 여러 대씩 띄운다. 이 문서는 그 코드를 **종류와 무관한 추상(core)** 과 **종류별 구현(melsec·modbus·opcua)** 으로 나눈 구조를 적는다.

## 1. 왜 바꾸는가 — 바꾸기 전

세 종류를 vplc 로 합친 직후(커밋 7873c4c) 패키지는 이랬다.

| 위치 | 종류 무관 | MELSEC 전용 |
|---|---|---|
| `machine` | `VirtualDevice`, `VirtualDeviceFactory`, `VirtualProgrammableControllerManager`, 연결 이력 이벤트 | `VirtualProgrammableController`·Builder, `McResponder`, `Memory`·`MemoryInfo`·`WordInfo`, `Message*`, `ClientChannel*`, 통신 위임·부트스트래퍼 |
| `transport` | — | Netty MC 코덱 |
| `Types` | `PlcStatus`, `Protocol` | `MemoryType`, `FrameFormat` |
| `endpoints/VirtualDeviceController` | — | Modbus·OPC UA 전용 API 를 한 곳에 |

문제:

1. `machine` 이 이름과 달리 사실상 MELSEC 이고, 종류 무관한 것이 섞여 있다.
2. `VirtualDeviceFactory` 가 `switch(protocol)` 로 세 종류를 다 안다 — 종류를 더하면(S7 등) 공통 코드를 고친다.
3. 상태 규칙(실행 중일 때만 일시정지 등)을 종류마다 따로 구현해 이미 어긋났다(MELSEC 은 정지 중 일시정지를 받는다, Modbus·OPC UA 는 409).
4. 종류별 뒷정리(OPC UA 태그 지우기)가 공통 관리 서비스에 들어가 있다.
5. 메모리 API 가 종류마다 따로다 — MELSEC `/manipulation/*`, Modbus `/modbus/{id}/{영역}`, OPC UA `/opcua/{id}/tags`. 화면은 Modbus 비트를 직접 묶고 풀었다.

## 2. 구조

```
com.wow.be.tc.vplc
├── Types                       PlcStatus·Protocol (종류 무관)
├── core/                       종류와 무관한 것만
│   ├── VirtualPlc              공통 생명주기 계약(아이디·이름·종류·상태, 시작·정지·일시정지·재개·해제, 연결 끊기)
│   ├── AbstractVirtualPlc      상태 전이 규칙 한 곳 + 상태 변경 알림(SSE) — 종류는 doStart·doStop·doPause·doResume·doRelease 만
│   ├── VirtualPlcProvider      종류별 SPI — protocol(), normalize(info), create(info), onDelete(id)
│   ├── VirtualPlcProviders     빈으로 모은 Provider 들 — 종류로 찾는다(공통 코드는 종류를 모른다)
│   ├── VirtualPlcRegistry      게시된(메모리에 올라온) 가상 PLC 들
│   ├── VirtualPlcEventKind     상태 변경 알림 종류
│   ├── memory/                 메모리 능력 인터페이스
│   │   ├── AreaMemory          영역(종류·번호)·값 읽기·쓰기·지우기 — MELSEC·Modbus
│   │   ├── TagMemory           태그 목록·더하기·지우기·값 — OPC UA
│   │   └── MemoryService       게시된 가상 PLC 의 능력으로 메모리를 다룬다
│   └── connection/             연결 이력 이벤트(이벤트·디스패처·리스너·ConnectionEvents)
├── melsec/                     MELSEC MC — MelsecVirtualPlc(예전 VirtualProgrammableController)·Builder·McResponder·Memory…, MelsecTypes(MemoryType·FrameFormat), MelsecProvider
│   └── transport/              Netty MC 코덱
├── modbus/                     ModbusVirtualPlc·ModbusPorts·ModbusTcpServer·ModbusPdu·ModbusDevice, ModbusProvider, ModbusController(응답 지연)
├── opcua/                      OpcUaVirtualPlc·VirtualOpcUaServer·TagType, 태그 엔티티·저장소, OpcUaProvider(지울 때 태그도), OpcUaController(엔드포인트 정보)
├── management/                 VPLC 테이블·이력·연결 이력 — 종류를 모른다(Provider 에 맡긴다)
├── manipulation/               공통 제어(게시·시작·정지·일시정지·재개) + MELSEC 예전 메모리 API(/manipulation/*)
├── endpoints/                  공통 REST — 등록·게시·제어·연결·연결 끊기, 메모리(/memory)
├── lifecycle/                  기동 때 올리기, 종료 때 정리
└── security/ · utils/
```

### 2.1 VirtualPlcProvider — 종류별 SPI

각 종류 패키지가 Spring 빈 하나를 낸다.

| 메서드 | 하는 일 |
|---|---|
| `protocol()` | 어느 종류인가 |
| `normalize(info)` | 등록·수정 때 그 종류의 기본값을 채우고 검사(틀리면 400) — Modbus 단위 ID·칸, OPC UA 경로 등 |
| `create(info)` | 게시 때 메모리에 올릴 객체(포트는 아직 열지 않는다) |
| `onDelete(id)` | 지울 때 그 종류의 뒷정리(OPC UA 태그) |

공통 코드(`management`·`manipulation`·`lifecycle`)는 `VirtualPlcProviders` 로 종류를 찾을 뿐 `switch` 가 없다. 종류를 더하려면 패키지 하나와 Provider 빈 하나를 더하면 된다.

### 2.2 AbstractVirtualPlc — 상태 규칙 한 곳

| 동작 | 받는 상태 | 결과 | 다른 상태 |
|---|---|---|---|
| 시작 | PUBLISHED·STOPPED | RUNNING(`doStart` — 포트 열기) | 409 |
| 일시정지 | RUNNING | PAUSED(`doPause`) | 409 |
| 재개 | PAUSED | RUNNING(`doResume`) | 409 |
| 정지 | RUNNING·PAUSED → `doStop`, 그 밖은 상태만 | STOPPED | — |
| 해제 | 실행 중이면 `doStop` 뒤 `doRelease` | RELEASED | — |

상태가 바뀌면 SSE(`VirtualPlcEventKind` — STARTED·STOPPED·PAUSED·RESUMED)를 보낸다(예전에는 MELSEC 만).
기동 때 DB 상태 되살리기(`VirtualPlcLoader`) — RUNNING 은 시작, PAUSED 는 시작 뒤 일시정지, STOPPED 는 정지 상태로. 예전에는 상태 글자만 붙이고 시작하지 않아 PAUSED 였던 PLC 는 포트가 닫혀 있었다.

### 2.3 메모리 능력 — AreaMemory · TagMemory

세 종류의 메모리 모양이 다르므로 한 API 로 억지로 묶지 않고 **능력(capability)** 으로 나눈다.

- **AreaMemory**(MELSEC·Modbus) — "영역 + 번호 + 값". 영역 하나는 `{code, label, unit(WORD·BIT), size, hexAddress, bitDevice}`.
  - MELSEC: 영역 = 메모리 종류(D·M·W…), 단위 WORD(비트 디바이스도 워드로 — `bitDevice` 면 화면이 비트마다 주소를 붙인다), W·B·X·Y·SW 주소는 16진.
  - Modbus: 영역 HR·IR(WORD), CO·DI(BIT), 번호는 프로토콜 주소(0부터).
  - 값은 단위 그대로 — WORD 는 0~65535(쓰기는 -32768 도), BIT 는 0·1.
- **TagMemory**(OPC UA) — 태그 목록(지금 값)·더하기·지우기·값 바꾸기.

### 2.4 REST

공통(`/api/v1/vplc`):

| 요청 | 내용 |
|---|---|
| `management/*`, `control/start·stop·pause·resume/{id}` | 예전 그대로(등록 본문에 `protocol`·`settings`) |
| `POST /control/drop-connections/{id}` | 연결 끊기 — 모든 종류 |
| `GET /memory/{id}` | 메모리 능력 — `{kind: AREA, areas: [...]}` 또는 `{kind: TAG}` |
| `GET /memory/{id}/area/{area}?start=&count=` · `PUT` `{start, values}` · `POST …/clear` | AreaMemory — MELSEC·Modbus |
| `GET /memory/{id}/tags` · `POST` · `PUT /memory/{id}/tags/{path}` `{value}` · `DELETE` | TagMemory — OPC UA(정의는 DB 에도) |

종류별:

| 요청 | 패키지 |
|---|---|
| `POST /control/delay/{id}` `{delayMs}` | modbus — 응답 지연 |
| `GET /opcua/{id}` | opcua — 엔드포인트·네임스페이스 번호·세션 수 |
| `POST /manipulation/read·write·clear/all` | MELSEC 예전 API(프레임·ASCII 문자열 쓰기) — e2e 가 쓴다 |

없어지는 것: `/modbus/{id}/{영역}`(→ `/memory/{id}/area/{영역}`), `/opcua/{id}/tags`(→ `/memory/{id}/tags`).

## 3. 진행 단계

| 단계 | 내용 | 바깥 동작 |
|---|---|---|
| 1 | 패키지 정리 — `core`·`melsec`(+`transport`) 로 옮기고 이름을 바꾼다(`VirtualDevice`→`VirtualPlc`, Manager→`VirtualPlcRegistry`, `VirtualProgrammableController`→`MelsecVirtualPlc`, `MemoryType`·`FrameFormat`→`MelsecTypes`). 시험도 `melsec` 로 | 같다 |
| 2 | Provider SPI — 팩토리 `switch`·OPC UA 태그 지우기를 Provider 로, 종류별 REST 를 각 패키지 컨트롤러로 | 같다 |
| 3 | `AbstractVirtualPlc` — 상태 규칙 한 곳, 세 종류가 같은 규칙. 기동 때 PAUSED 되살리기 | 잘못된 상태의 제어가 409 로 같아진다 |
| 4 | 메모리 능력 인터페이스와 `/memory` API, 화면·e2e 를 그쪽으로 | Modbus·OPC UA 메모리 경로가 바뀐다 |

DB 는 바뀌지 않는다(VPLC·`VPLC_OPCUA_TAG` 그대로).

## 4. 결과 (2026-11-01, 1~4단계)

| 단계 | 한 것 |
|---|---|
| 1 | `machine`·`transport` 를 `core`(VirtualPlc·VirtualPlcRegistry·VirtualPlcEventKind·connection/)와 `melsec`(MelsecVirtualPlc·Builder·McResponder·Memory…·transport/)로. `MemoryType`·`FrameFormat` → `melsec.MelsecTypes`(Types 에는 PlcStatus·Protocol 만). 부트스트래퍼 클래스 이름 문자열도 `melsec.transport` 로. 레지스트리는 `instance()`·`register`·`get`·`release`·`all` |
| 2 | `VirtualPlcProvider`(+`VirtualPlcProviders`) — `MelsecProvider`·`ModbusProvider`·`OpcUaProvider`. 팩토리 `switch` 와 관리 서비스의 OPC UA 태그 지우기가 없어졌다(`onDelete`). 종류별 REST 는 `ModbusController`(응답 지연)·`OpcUaController`(엔드포인트 정보), 공통은 `MemoryController`(메모리·연결 끊기) |
| 3 | `AbstractVirtualPlc` — 세 종류가 상속. MELSEC 의 start·stop·pause·resume·release 는 `doStart`… 로, SSE 알림(예전 MELSEC 만)은 공통으로. Modbus·OPC UA 는 따로 들고 있던 상태·실행 여부를 버렸다. 기동 때 PAUSED 는 시작 뒤 일시정지 |
| 4 | `AreaMemory`(MELSEC·Modbus 가상 PLC)·`TagMemory`(OPC UA Provider)·`MemoryService`, `/memory` API. 화면은 영역 정보(이름·크기·16진·비트 디바이스)를 서버에서 받고(프런트의 MELSEC 메모리 표·Modbus 영역 표로 주소를 만들지 않는다), MELSEC·Modbus 같은 길로 읽고 쓴다 |

- 바뀐 동작: 실행 중이 아닌 가상 PLC 의 일시정지·재개, 실행 중의 시작은 세 종류 모두 409(예전 MELSEC 은 받았다). e2e `test_plc_mc.py` 정리 단계의 재개는 409 도 받게 고쳤다(예전에는 이미 실행 중이어도 200 이라 몰랐다).
- 없어진 API: `/modbus/{id}/{영역}`, `/opcua/{id}/tags` → `/memory/{id}/area/{영역}`·`/memory/{id}/tags`. 화면의 MELSEC 예전 메모리 호출(`/manipulation/*`)도 `/memory` 로 — `/manipulation/*` 은 e2e 를 위해 남겼다.
- 시험: vplc 단위 20(새 — `MelsecAreaMemoryTest`, `ModbusVirtualPlcTest` 상태 규칙·영역), e2e `test_vplc_http_methods` 43(메모리 능력 API 6·상태 규칙 2 더함)·`test_plc_vmodbus` 8·`test_plc_opcua` 30·`test_plc_points` 44·`test_plc_mc` 11/12(남은 하나는 tc 를 시험용 시간 초과 3초로 띄워야 하는 항목), Playwright 24.
