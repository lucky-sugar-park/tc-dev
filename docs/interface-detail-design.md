# 인터페이스 세부 정의 설계 (MELSEC · Modbus TCP · OPC UA)

> 상태: **설계 확정, A·B·C단계 구현, RAW 제거(I7)** (2026-10-31, v0.4.0 — 사용자가 "문서화 후 A단계부터" 결정). 대상: `wow-tc-backend/tc`, `wow-tc-frontend`, (D단계) `vplc`.
> 관련: [plc-protocol-design.md](plc-protocol-design.md) §4(데이터 모델)·§5(드라이버 경계), [frontend-design.md](frontend-design.md) §5·§9.

## 1. 왜

화면에서 "인터페이스의 세부 정보(헤더·데이터)를 정의하는 곳이 없다"는 지적이 나왔다. 조사해 보니 정보가 **없는 것이 아니라 흩어져 있고 보이지 않는다**.

| 옛 RAW 에 있던 것 | 지금(POINTS) 있는 곳 |
|---|---|
| 프레임·네트워크/PC/IO/국 번호·감시 타이머 | **커넥터 설정**(PLC 하나에 하나) |
| 명령(0401/1401)·서브 명령·디바이스 코드·시작 주소·길이 | **드라이버가 점에서 만든다**(묶기 포함) |
| 데이터 항목(순서·형·길이) | **점 표**(POINTS 수정 대화상자) |

그런데 다음이 비어 있다.

1. **실제로 PLC 에 무엇을 보내는지 볼 곳이 없다.** 드라이버가 프레임을 만들자마자 보낸다(`PlcDriver.execute`) — 보내지 않고 계획만 세우는 길이 없다.
2. **인터페이스마다 다른 프로토콜 설정을 줄 수 없다.** `PlcInterfaceCommand` 가 커넥터 설정만으로 작업을 만든다. 현장에서는 인터페이스마다 달라야 하는 값이 있다(아래 §2).
3. **점 표현력이 모자라다.** 워드 안 비트(`D100.3`)는 거절되고, 문자열은 UTF-8 만, 배율·단위가 없다.
4. **읽기 전용 상세 보기가 없다.** POINTS 정의는 수정 권한이 있어야 열리고, RAW 행은 "RAW 보기"뿐이다.

## 2. 프로토콜별로 모자란 것

| | 인터페이스마다 달라야 하는 것 | 점마다 필요한 것 |
|---|---|---|
| **MELSEC MC** | 다른 PLC 로 중계(`networkNo`·`stationNo`·`pcNo`·`ioNo`), 느린 명령의 `monitoringTimer`, 묶을 때 빈 칸을 건너뛰지 않기(빈 주소를 읽으면 오류인 장비·`mergeGap`) | 워드 안 비트(`D100.3`), 문자열 인코딩(ASCII·Shift-JIS·UTF-16LE) |
| **Modbus TCP** | **`unitId`**(게이트웨이 한 대 뒤에 RTU 장비 여러 대 — 흔하다), 쓰기 FC 강제(`writeFunction`: FC6 만·FC16 만 받는 장비), 장비별 요청 한도(`maxRegistersPerRequest`), `mergeGap` | 레지스터 안 비트(`HR40001.3`), 문자열 바이트 교환 |
| **OPC UA** | 읽기 방식(`accessMode` READ·SUBSCRIBE), `samplingIntervalMs`·`publishingIntervalMs`·데드밴드, 돌려줄 시각(source·server) | NodeId(주소 칸 그대로), 노드 형 확인 — **노드 찾아보기(browse) 화면** |
| **공통** | — | 배율·오프셋·단위, 쓰기 값 범위(min·max) |

## 3. 모델 — 세 층

```
커넥터 settings (연결 단위, 지금 그대로)
   └─ 인터페이스 settings (새로 — 커넥터 값 덮어쓰기 + 인터페이스 전용 옵션, 드라이버가 허용 키를 정한다)
        └─ 점 (주소·형·개수·길이·순서 + 새 options — 비트·인코딩·배율·단위)
```

- 실행할 때 **유효 설정 = 커넥터 settings ⊕ 인터페이스 settings**(인터페이스 값이 이긴다). 드라이버가 정규화·검증한다 — 덮어쓸 수 없는 키(`frame`·`healthCheckIntervalSec` 처럼 연결에 묶인 것)는 400.
- 화면은 **스키마로 그린다**. `/protocols` 의 설명(`ProtocolDescriptor`)에 `interfaceSettings`·`pointOptions` 를 더하고, 커넥터 폼처럼 이것을 보고 입력 칸을 만든다. 비운 칸에는 물려받는 값("커넥터 값: 1")을 보인다.
- 저장: `INTERFACE.SETTINGS_JSON`, `INTERFACE_POINT.OPTIONS_JSON` (migration 010). 비트는 주소 문법(`D100.3`)으로 받아 칼럼을 늘리지 않는다.

**RAW 는 없앴다(결정 I7, 2026-10-31).** 처음에는 레거시 운영 정의를 옮기는 동안 RAW(MELSEC 3E 헤더·데이터 바이트) 실행을 남기고 변환기로 줄이려 했으나, 운영 중인 시스템이 없어 RAW 와 변환기를 모두 지웠다. 인터페이스는 점으로만 정의한다 — [interfaces.md](interfaces.md).

## 4. 화면

인터페이스 등록·수정 대화상자를 탭으로 나눈다(B단계에서 완성, A단계는 미리보기만).

| 탭 | 내용 | 단계 |
|---|---|---|
| 기본 | 이름·PLC 커넥터·타입·사용·(WRITE)쓴 뒤 다시 읽기·설명 | 지금 |
| 프로토콜 설정 | 스키마로 그린 인터페이스 settings(비우면 커넥터 값) | B |
| 점 | 점 표 + 점 options | 지금 / C |
| 요청 미리보기 | 지금 정의로 PLC 에 보낼 요청 목록(기능·대상·개수·담긴 점·프레임 16진) | **A** |

목록의 ⋮ 메뉴에 **상세 보기**(읽기 전용, 조회 권한만 있으면)를 둔다 — 기본 정보·유효 커넥터 설정·점·요청 미리보기. **A**.

## 5. 단계

| 단계 | 내용 | DB·재기동 |
|---|---|---|
| **A** | 드라이버 `plan`(보내지 않고 요청으로 나누기)과 실행 경로 공유, 미리보기 API, 상세 보기·폼의 요청 미리보기 | 없음(jar 교체 — 재기동은 필요) |
| B | 인터페이스 settings: MELSEC·Modbus 덮어쓰기와 전용 옵션(`mergeGap`·`writeFunction`), 폼 탭, 변환기 보강 | migration 010, 재기동 |
| C | 점 options: 비트(`D100.3`·`HR40001.3`), 문자열 인코딩·바이트 교환, 배율·오프셋·단위·쓰기 범위 | migration, 재기동 |
| D | OPC UA 드라이버(Eclipse Milo — Nexus 확인 필요)·vplc OPC UA, 노드 찾아보기, SUBSCRIBE 결과를 전달 정의 경로로 | 라이브러리, 재기동 |

B·C 는 tc 재기동이 필요하다(MES 요청이 끊긴다) — 한 번의 배포로 묶는다.

## 6. 결정

| # | 질문 | 제안 | 상태 |
|---|---|---|---|
| I1 | 인터페이스 settings 저장 | **JSON 칼럼 하나**(`SETTINGS_JSON`) + 드라이버 검증 | 제안(B 전에 확정) |
| I2 | 비트 표기 | **주소 문법**(`D100.3`, `HR1.15`) — 형은 BOOL·개수 1, 비트 번호는 MELSEC 16진(0~F)·Modbus 10진(0~15), 워드 디바이스에만. **읽기 전용**(쓰기는 읽고-바꾸고-쓰기 사이에 PLC 가 바꾼 다른 비트를 덮어쓸 수 있어 막는다). 설명: [word-bit-points.md](word-bit-points.md) | 확정(2026-11-01, 사용자) |
| I3 | 배율·단위 변환을 tc 가 할지 | **tc 가 한다** — 점마다 scale·offset·unit, 읽을 때 곱하고 쓸 때 거꾸로 푼다. MES 는 공학 단위 값을 받는다 | 확정(2026-10-31, C단계) |
| I4 | OPC UA SUBSCRIBE 결과를 어디로 | 스케줄 Job 과 같은 **전달 정의 경로**(WebHook·DB_TABLE·CALLBACK_URL) | 확정(2026-10-31, D단계) |
| I5 | RAW 편집 | **만들지 않는다**(변환으로 줄인다) — I7 로 RAW 자체를 없애 의미가 없어짐 | 확정 → I7 이 대신함 |
| I7 | RAW 를 남길지 | **없앤다** — 운영 중인 시스템이 없다. `header_prop`·`data_prop` 표, 응답 인터페이스(REPLY), 변환기, 옛 MELSEC 요청·응답 객체, 화면의 RAW 보기·변환·되돌리기, 정의 방식(POINTS/RAW) 구분을 지운다(migration 011) | 확정(2026-10-31, 사용자) |
| I6 | 미리보기의 쓰기 값 | 요청 `data` 를 주면 그 값, 없으면 **형의 0 값**(BOOL false·문자열 빈 값)으로 — 프레임 모양을 보이는 것이 목적 | 확정(A) |

## 7. A단계 구현 명세

### 7.1 드라이버

- `PlcDriver.plan(PlcOperation)` → `List<PlannedRequest>`. 보내지 않는다. 점 검증 실패는 `IllegalArgumentException`(400), 쓰기 값이 틀리면 400.
- `PlannedRequest(seq, function, target, count, unit, points, frameHex)` — 예: `1 · 0401/0000 일괄 읽기(워드) · D100 · 12 · 워드 · [d100, d101] · 500000FF…`, `1 · FC3 Read Holding Registers · HR0 · 12 · 레지스터 · […] · 0000…`.
- **실행과 같은 길**: 두 드라이버 모두 "묶기 → 요청 프레임 만들기"를 단계 목록 메서드로 떼어 `execute` 와 `plan` 이 함께 쓴다. 미리보기가 실제 보내는 것과 어긋나지 않게 하려는 것이다.
- 4E 일련번호·Modbus 트랜잭션 ID 는 보낼 때 어댑터가 채운다 — 미리보기 프레임에서는 0 이다(화면에 적는다).

### 7.2 API

`POST /api/v1/ifsvc/management/plan` (권한 `tc.interface` R) — 본문은 인터페이스 정의(저장 전 폼 그대로도 된다)와 선택 `data`(쓰기 값).

```json
{ "interfaceInfo": { "name": "…", "plcName": "plc-e2e", "interfaceType": "READ", "mode": "POINTS", "points": [ … ] },
  "data": { "d100": 7 } }
```

응답:

```json
{ "protocol": "MELSEC_MC", "plcName": "plc-e2e", "settings": { "frame": "3E_BINARY", "networkNo": 0, … },
  "requests": [ { "seq": 1, "function": "0401/0000 일괄 읽기(워드)", "target": "D100", "count": 2, "unit": "워드",
                  "points": ["d100", "d101"], "frameHex": "500000FF03FF000C001000010400006400 00A80200" } ],
  "notes": ["4E 일련번호는 보낼 때 채운다"] }
```

- RAW 인터페이스·잘못된 점·없는 커넥터는 400(등록 검증과 같은 길). 커넥터가 게시되지 않아도 된다(연결하지 않는다).

### 7.3 화면

- 인터페이스 목록 ⋮ 메뉴 **상세 보기**(POINTS) — 읽기 전용 대화상자: 기본 정보, 커넥터(프로토콜·유효 설정), 점 표, 요청 미리보기.
- 등록·수정 대화상자 아래에 **요청 미리보기** 접는 칸 — 지금 편집 중인 정의로 `/plan` 을 부른다.

### 7.4 시험

- 단위: `MelsecDriverTest`·`ModbusDriverTest` 에 plan 이 execute 가 보내는 프레임과 같음(기록하는 `FrameExchanger` 로 비교), 묶기 개수.
- e2e: `test_plc_points.py` 에 `/plan`(READ·WRITE·RAW 400·없는 커넥터 400·잘못된 주소 400).
- 화면: Playwright `plc.spec.ts` 에 상세 보기·미리보기.

### 7.5 구현 결과 (2026-10-31)

| 것 | 결과 |
|---|---|
| 드라이버 | `PlcDriver.plan`, `PlannedRequest`. MELSEC·Modbus 모두 묶기·프레임 만들기를 `execute` 와 같은 메서드로 쓴다(`readRequest`·`writeRequest`·`runs`). 단위 테스트가 미리보기 프레임과 실제로 보낸 프레임을 비교 — tc 단위 103개 통과 |
| API | `POST /api/v1/ifsvc/management/plan`(`RequestPlanInfo`) — 로컬 tc 재기동 뒤 API 문서 등록·인증 없이 401 확인 |
| 화면 | 인터페이스 ⋮ **상세 보기**(POINTS)·등록/수정 대화상자의 **요청 미리보기** — 저장 전 정의(D100 INT16)로 `0401/0000 · D100 · 1워드` 와 프레임이 나오는 것을 확인 |
| 남은 것 | e2e `/plan` 5개 항목은 시험 관리자 계정이 있는 환경에서 돌린다. Playwright 항목은 아직 없다. 실제 PLC(4E 등)로 프레임 대조는 하지 않았다 |

## 8. B단계 구현 — 인터페이스 settings (2026-10-31)

| 것 | 내용 |
|---|---|
| 모델 | `InterfaceInfo.settings`(JSON, `INTERFACE.SETTINGS_JSON` — migration 010). 비면 커넥터 설정 그대로. RAW 는 settings 를 받지 않는다(400) |
| 스키마 | `ProtocolDescriptor.interfaceSettings` — 서버 검증(`InterfaceSettings.normalize`)과 화면 폼이 같은 목록을 쓴다. 목록에 없는 키(`frame`·`addressBase`·`healthCheckIntervalSec` 처럼 연결에 묶인 것)는 400 |
| MELSEC | 덮어쓰기 `networkNo`·`pcNo`·`ioNo`·`stationNo`·`monitoringTimer`·`maxWordsPerRequest`, 전용 `mergeGap`(0~64, 기본 8) |
| Modbus | 덮어쓰기 `unitId`·`byteOrder`·`wordOrder`·`maxRegistersPerRequest`·`maxCoilsPerRequest`, 전용 `writeFunction`(AUTO·SINGLE·MULTIPLE — SINGLE 은 칸마다 FC5·6, MULTIPLE 은 하나여도 FC15·16)·`mergeGap`(레지스터·코일 같은 수, 비우면 8·16) |
| 유효 설정 | `PlcDriver.effectiveSettings` = 커넥터 정규화 설정 ⊕ 인터페이스 settings. 실행(`PlcInterfaceCommand`)·미리보기가 같은 길이고, 작업(`PlcOperation.settings`)에 실려 원격 어댑터(다중화)에도 간다 |
| 변환기 | RAW 머리의 네트워크·PC·I/O·국 번호·감시 타이머가 커넥터와 다르면 거절하던 것을 인터페이스 settings 로 옮긴다(경고에 적음). 적용하면 함께 저장 — **이후 I7 로 변환기째 지움** |
| 화면 | 등록·수정 대화상자 탭 — 기본 · 프로토콜 설정(비운 칸에 "커넥터 값 …") · 점 · 요청 미리보기. 상세 보기에 "이 인터페이스의 설정", RAW 변환 미리보기에 옮길 설정. 커넥터 폼과 설정 칸을 공용(`SettingsFields`)으로 |
| 시험 | 단위 107개 통과(설정 검증·덮어쓰기 프레임·mergeGap·writeFunction 실행·변환기). e2e `test_plc_points.py` 에 settings 4개, Playwright 에 설정 탭·상세 보기 — 둘 다 시험 계정이 있는 환경에서 돌린다 |
| 운영 반영 | migration 010(로컬은 ddl-auto 로 생김), jar 교체·재기동 |

## 9. C단계 명세 — 점 options (2026-11-01)

이번 C단계는 **워드 안 비트(I2)** 와 **배율·오프셋·단위·쓰기 범위(I3)** 만 한다. 문자열 인코딩(Shift-JIS·UTF-16LE)·바이트 교환은 현장 요구가 나오면 따로 한다.

### 9.1 워드 안 비트 (I2 — 설명 [word-bit-points.md](word-bit-points.md))

| 것 | 내용 |
|---|---|
| 주소 | MELSEC `D100.A`(비트 번호 16진 한 자리 0~F), Modbus `HR1.15`·`40001.3`·`IR10.8`(10진 0~15). 워드 디바이스·레지스터에만 — 비트 디바이스(`M10.3`)·코일·이산 입력(`CO1.2`)은 400 |
| 점 | 형 `BOOL`, 개수 1. 바이트·워드 순서 재정의는 쓰지 않는다 |
| 읽기 | 그 워드 1칸을 차지하는 점으로 묶기에 들어간다 — 같은 워드의 비트 점들은 한 요청. 워드 값(MELSEC 리틀, Modbus 는 유효 `byteOrder`)에서 `(word >> bit) & 1` |
| 쓰기 | **막는다** — `WRITE` 인터페이스에 비트 점이 있으면 등록·실행 400(읽고-바꾸고-쓰기 사이에 PLC 가 바꾼 다른 비트를 덮어쓴다) |
| 미리보기 | 대상은 워드(`D100` 1워드), 담긴 점에 비트 점 이름 |

### 9.2 배율·오프셋·단위·쓰기 범위 (I3)

| 것 | 내용 |
|---|---|
| 점 필드 | `scale`(기본 1, 0 은 400), `offset`(기본 0), `unit`(표시용 글자, 예 `℃`), `min`·`max`(쓰기 값 범위, 공학 값 기준) |
| 쓸 수 있는 형 | 숫자형(INT16~FLOAT64)만. BOOL·STRING·BYTES 에 있으면 400 |
| 읽기 | `공학 값 = 원시 값 × scale + offset`. 배율·오프셋이 있으면 응답 값은 실수(예 원시 253, scale 0.1 → `25.3`). 배열은 원소마다. 계산은 BigDecimal 로 해 `25.299999…` 가 나오지 않게 |
| 쓰기 | 요청 값(공학 값)을 `min`·`max` 로 검사(벗어나면 400) → `원시 = (공학 − offset) ÷ scale` → 정수형은 반올림 → 형 범위 검사(지금과 같음) |
| 어디서 | 프로토콜과 상관없는 한 곳(`PointScaling`)에서 — 커맨드가 작업을 만들기 전(쓰기 값)과 결과를 받은 뒤(읽은 값, readBack 포함). 드라이버와 원격 어댑터는 원시 값만 다룬다. 요청 미리보기도 쓰기 값을 같은 함수로 원시로 바꿔 프레임을 만든다 |
| 단위 | 응답에 싣지 않는다(값만). 정의·상세 보기·점 칼럼에 보인다 |
| 저장 | `INTERFACE_POINT` 칼럼 `SCALE`·`OFFSET_VALUE`·`UNIT`·`MIN_VALUE`·`MAX_VALUE`(migration 013). 처음 생각한 `OPTIONS_JSON` 대신 칼럼 — 값이 정해져 있고 DB 에서 바로 읽힌다 |
| 변경 감지 | 공학 값으로 비교(응답 `data` 와 같은 값) |

### 9.3 화면

- 점 편집기에 칸 `배율`·`오프셋`·`단위`·`최소`·`최대`(숫자형일 때만 입력). 주소 예시에 `D100.3`·`HR1.15`.
- 목록의 점 칼럼은 `temp=D5004(℃)` 처럼 단위를 붙인다. 상세 보기 점 표에 배율·오프셋·단위·범위.

### 9.4 시험

- 단위: MELSEC·Modbus 주소 파서(비트 꼬리·진법·범위·비트 디바이스 거절), 비트 점 묶기·풀기, 쓰기 거절, `PointScaling`(읽기·쓰기·반올림·범위·배열·형 거절).
- e2e `test_plc_points.py`: vplc `D` 에 값을 쓰고 비트 점·배율 점으로 읽기, 배율 쓰기 → 원시 값 확인, 범위 밖 400, 비트 점 WRITE 거절.
- Playwright: 점 편집기에 배율 칸, 점 칼럼의 단위.

### 9.5 C단계 결과 (2026-11-01)

| 것 | 내용 |
|---|---|
| 비트 | `MelsecAddress`·`ModbusAddress` 에 비트 꼬리, 검증은 공통 `WordBits`(BOOL·개수 1·순서 없음·WRITE 거절), 값은 `ValueCodec.bitOf` |
| 배율 | `PointScaling` — 등록 검증(`validatePoints`), 실행(`PlcInterfaceCommand`: 쓰기 값 → 원시, 결과 → 공학, readBack 포함), 미리보기(쓰기 값 → 원시) |
| 저장 | `INTERFACE_POINT` 의 `SCALE`·`OFFSET_VALUE`·`UNIT`·`MIN_VALUE`·`MAX_VALUE`(migration 013, 로컬은 ddl-auto 로 생김) |
| 화면 | 점 편집기 배율·오프셋·단위·최소·최대 칸(숫자형만), 워드 안 비트 주소면 형 BOOL·쓰기 인터페이스에 경고, 점 칼럼에 단위, 상세 보기 점 표에 배율·범위 |
| 시험 | 단위 101개(새 7개), e2e `test_plc_points.py` 44개(C 11개 — vplc D 메모리로 비트·배율 읽기, 배율 쓰기 → PLC 원시 366, 범위 밖·비트 쓰기·BOOL 배율·10진 비트 거절) |
