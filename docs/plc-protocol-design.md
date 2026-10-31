# PLC 프로토콜 일반화 설계 (MELSEC · Modbus TCP · OPC UA)

> 상태: **설계안 — 결정 대기** (2026-10-31, v0.3.0). 대상: `wow-tc-backend/tc`, `vplc`, e2e.
> 할 일: [backend-todo.md](backend-todo.md) #12(PLC 보강), #13(Modbus·OPC UA). 프론트 재작성 전에 끝낸다 — 커넥터·인터페이스 화면이 이 모델 위에 선다.
> 관련: [dispatch-redesign.md](dispatch-redesign.md) §4·단계 7(값 코덱), [system-overview.md](system-overview.md) §1, [plc-ha-design.md](plc-ha-design.md) §3.1·§4(다중화 전달).

## 1. 목표와 범위

- **목표**
  - 같은 opcode·같은 REST·같은 전달·감사·다중화 위에서 **MELSEC, Modbus TCP, OPC UA** PLC 를 읽고 쓴다.
  - 사용자(운영자·화면)는 **프레임 바이트가 아니라 "어느 주소를 어떤 형으로"** 정의한다.
  - 지금 운영 중인 MELSEC 정의는 그대로 돈다(호환 모드). 새 정의부터 새 모델을 쓴다.
  - 프론트가 만들 API 계약(커넥터·인터페이스·시험 읽기)을 여기서 확정한다.
- **범위**
  - 1차: 공통 모델·드라이버 경계, MELSEC 을 새 모델로, MELSEC 보강(#12), Modbus TCP, 다중화 전달 계약 변경.
  - 2차: OPC UA 읽기·쓰기(구독은 3차). 1차 설계가 OPC UA 를 담을 수 있는지는 여기서 확인한다.
- **범위 밖**: OPC UA 구독·이벤트, MELSEC 시리얼(MC 1C 등), Modbus RTU(시리얼), PLC 프로그램 업·다운로드.

## 2. 지금 구조 — 무엇이 MELSEC 에 묶여 있나

```mermaid
flowchart LR
    REQ["REST 요청<br/>opcode + data{name:value}"] --> CMD[PlcInterfaceCommand]
    CMD -->|"헤더 속성 = MC 3E 프레임 바이트<br/>데이터 속성 = 리틀엔디안 바이트"| PREQ["PlcRequest<br/>(MelsecPlcRequest)"]
    PREQ -->|"convertRequest() → byte[]"| AD["ProgrammableControllerAdapter<br/>(Melsec)"]
    AD -->|"응답 byte[]"| PRES["PlcResponse<br/>(MelsecPlcResponse)"]
    PRES -->|"응답 인터페이스의 헤더 길이로 자르고<br/>데이터 속성을 리틀엔디안으로 해석"| RESP["응답 data{name:value}<br/>resultCode = MELSEC 종료 코드"]
    AD -. 다중화 .-> REMOTE["RemotePlcAdapter<br/>프레임 바이트를 주인에게"]
```

| 어디 | MELSEC 에 묶인 것 | 근거 |
|---|---|---|
| 인터페이스 정의 | **요청 헤더 속성이 MC 3E 프레임 그대로** — 운영자가 `subHeader=5000`, `deviceCode=A8`, `startAddress=640000`(리틀엔디안 16진), `wordCount=0200`, `command=0104` 를 손으로 적는다 | e2e `fixture-if-read.json`, `HEADER_PROP` 표 |
| 응답 인터페이스 | 응답 헤더(`subHeader`…`resultCode`)의 바이트 길이로 응답을 자르고, 나머지를 데이터로 본다. 읽기 하나에 인터페이스 두 개(요청·응답)가 든다 | `MelsecPlcResponse.splitHeaderAndReplyData` |
| 값 변환 | 리틀엔디안 고정, `DOUBLE` 을 4바이트 float 로 읽음, 문자열 UTF-8 | `PlcResponse.extractReplypData`, `PropInfo.extractRealDataAsBytes` |
| 커넥터 정의 | `messageFrameFormat`(BINARY/ASCII), 요청·응답 헤더 템플릿, `memTypes`, 포트·VIP 쌍 | `PlcConnectorInfo` |
| 응답 | `resultCode` 가 MELSEC 종료 코드 | `TcMessageResponse` 주석 |
| 변경 감지 | 응답 원본 바이트 비교 | `PlcResponseService` |
| 다중화 | 인스턴스 사이 계약이 "요청 바이트 → 응답 바이트" | `RemotePlcAdapter`, `/internal/plc/{name}/exchange` |
| 시험 장비 | vplc 는 MC 3E 서버만 | `vplc/transport` |

**코드 점검에서 찾은 결함** (호환 모드에도 영향 — 1단계에서 함께 고친다)

| # | 결함 | 영향 |
|---|---|---|
| M1 | ASCII 프레임 조립이 `headers.get("oppornentIONumber")`(오타)를 읽는다 | ASCII 커넥터는 요청마다 NPE — 사실상 BINARY 만 동작 |
| M2 | 명령 검사 `cmd[0]!=0x01 && cmd[1]!=0x04` | `&&` 라 READ 인터페이스에 쓰기 명령(`0114`)이 와도 통과 |
| M3 | `wordCount`·`reqDataLen` 을 운영자가 헤더에 직접 적는다(WRITE 데이터 길이와 맞는지 확인 안 함) | 정의 실수가 PLC 오류 응답으로만 드러난다 |
| M4 | 응답 길이를 응답 인터페이스 정의로만 확인, 프레임의 `respDataLen` 을 보지 않음(#12 "응답 길이 검증") | 잘린·이어 붙은 프레임을 잘못 해석할 수 있다 |

## 3. 바꾼 뒤 구조

```mermaid
flowchart LR
    REQ["REST 요청<br/>opcode + data{name:value}"] --> CMD[PlcInterfaceCommand]
    CMD -->|"인터페이스의 점(point) 목록<br/>D100:INT16, ns=2;s=Temp:FLOAT …"| OP["PlcOperation<br/>READ(points) / WRITE(points, values)"]
    OP --> SES["PlcSession (드라이버별)"]
    subgraph DRV["PlcDriver — 프로토콜마다 하나"]
        direction TB
        MEL["MELSEC<br/>주소 파싱 · 블록 묶기 · MC 3E/4E 프레임"]
        MOD["Modbus TCP<br/>FC 1/2/3/4/5/6/15/16 · 빅엔디안·워드 순서"]
        OPC["OPC UA<br/>Read/Write 서비스 · 노드 ID · 형 붙은 값"]
    end
    SES --- DRV
    SES -->|"PlcResult<br/>values{name:value} + status"| RESP["응답 data{name:value}<br/>plcStatus{protocol, code, text}"]
    SES -. 다중화 .-> REMOTE["RemotePlcSession<br/>작업(READ/WRITE)을 주인에게"]
```

- **경계를 "바이트 프레임"에서 "작업(읽기·쓰기할 점과 값)"으로 올린다.** 프레임을 만들고 해석하는 일은 전부 드라이버 안이다.
- 명령(`PlcInterfaceCommand`)·디스패치·감사·결과 전달·변경 감지·스케줄은 프로토콜을 모른다.
- 다중화도 작업 단위로 넘긴다(§7) — OPC UA 는 바이트 프레임이 없다(세션·보안 채널 위의 서비스 호출).

## 4. 데이터 모델

### 4.1 커넥터

| 필드 | 공통/프로토콜 | 내용 |
|---|---|---|
| `name`, `description`, `status1`(의도), `connection`(실제) | 공통 | 지금과 같음 |
| `protocol` | 공통 | `MELSEC_MC`, `MODBUS_TCP`, `OPC_UA` |
| `host`, `ports`, `localVips` | 공통(OPC UA 는 `endpointUrl` 로 대신) | 채널 = 포트·VIP 쌍(지금과 같음). Modbus 도 같은 방식 |
| `requestTimeoutMs` | 공통(선택) | 비우면 `plc.request.timeout-ms` |
| `settings` | **프로토콜별 JSON**, 드라이버가 검증 | 아래 표 |

| 프로토콜 | `settings` |
|---|---|
| `MELSEC_MC` | `frame`(`3E_BINARY`·`3E_ASCII`·`4E_BINARY`), `networkNo`, `pcNo`, `ioNo`, `stationNo`, `monitoringTimer`(250ms 단위), `maxWordsPerRequest`(기본 960) |
| `MODBUS_TCP` | `unitId`, `byteOrder`(기본 BIG), `wordOrder`(기본 BIG — 32비트 값의 워드 순서), `addressBase`(0 또는 1), `maxRegistersPerRequest`(기본 125), `maxCoilsPerRequest`(기본 2000) |
| `OPC_UA` | `endpointUrl`, `securityPolicy`(None·Basic256Sha256 …), `securityMode`, `auth`(`anonymous`·`username`), `username`, `password`(암호화 저장 — `TC_SECRET_KEY`, DB 연결과 같은 방식), `sessionTimeoutMs` |

지금의 `messageFrameFormat`·헤더 템플릿·`memTypes` 는 MELSEC `settings` 로 옮긴다(§8 이전).

### 4.2 인터페이스

| 필드 | 내용 |
|---|---|
| `name`(opcode), `plcName`, `interfaceType`(READ·WRITE), `use`, 전달 정의 | 지금과 같음 |
| `mode` | `POINTS`(새 모델) · `RAW`(지금의 헤더·데이터 바이트 속성, MELSEC 만 — 호환) |
| `points` | `POINTS` 일 때. 아래 |
| `replyName`, `headerProps`, `dataProps`, `dataLength` | `RAW` 일 때만(지금과 같음) |

**점(point)** — 이름 하나에 값 하나(또는 배열)

| 필드 | 내용 | 예 |
|---|---|---|
| `name` | 요청·응답 `data` 의 키 | `d100`, `temp` |
| `address` | 프로토콜별 주소 문자열(드라이버가 파싱·검증) | MELSEC `D100`, `M10`, `W1A`, `ZR20000` · Modbus `HR40001`/`hr:0`, `IR30001`, `CO1`, `DI10001` · OPC UA `ns=2;s=Line1.Temp` |
| `type` | `BOOL`, `INT16`, `UINT16`, `INT32`, `UINT32`, `INT64`, `FLOAT32`, `FLOAT64`, `STRING`, `BYTES` | `INT16` |
| `count` | 배열 길이(기본 1) — 값이 JSON 배열이 된다 | 10 |
| `length` | `STRING`·`BYTES` 의 바이트 수 | 20 |
| `byteOrder`, `wordOrder` | 선택 — 커넥터 기본값을 이 점만 바꿀 때 | `LITTLE` |
| `description` | | |

- **READ** 인터페이스: 점들을 읽어 `data{name: value}` 로 돌려준다. 응답 인터페이스(`replyName`)가 필요 없다 — 읽기 하나 = 인터페이스 하나.
- **WRITE** 인터페이스: 요청 `data` 에서 점 이름의 값을 읽어 쓴다. 빠진 값은 400. 쓴 뒤 응답 `data` 는 비거나(기본) 다시 읽은 값(선택 `readBack`).
- 드라이버는 점들을 **연속 주소끼리 묶어** 요청 수를 줄인다(MELSEC 같은 디바이스·연속 워드, Modbus 같은 영역·한도 안). 묶기는 드라이버 일이라 정의에는 드러나지 않는다.

**예 — 지금의 `plc.e2e.d100.read`(요청·응답 인터페이스 두 개, 헤더 바이트 19개)가 이렇게 된다**

```json
{ "name": "plc.e2e.d100.read", "plcName": "plc-e2e", "interfaceType": "READ", "mode": "POINTS",
  "points": [ { "name": "d100", "address": "D100", "type": "INT16" },
              { "name": "d101", "address": "D101", "type": "INT16" } ] }
```

### 4.3 응답

```json
{ "success": true, "data": { "d100": 77, "d101": 1 },
  "plcStatus": { "protocol": "MELSEC_MC", "code": 0, "text": "OK" },
  "resultCode": 0, "resultDescription": "SUCCESS", "changed": true, "...": "..." }
```

- `plcStatus` 를 더한다 — MELSEC 종료 코드, Modbus 예외 코드(1~11), OPC UA StatusCode(32비트)와 이름.
- `resultCode`·`resultDescription` 은 남긴다(호환). MELSEC 은 지금과 같고, 다른 프로토콜은 `plcStatus.code` 와 같은 값.
- 실패는 지금처럼 `success=false`, `ERR_502_TC_PLC_ERROR`(PLC 가 오류로 답함) · `ERR_503`(연결 없음) · `ERR_504`(시간 초과). 점마다 상태가 다른 OPC UA 는 하나라도 나쁘면 실패, 점별 상태는 `plcStatus.points` 에.

## 5. 드라이버 경계

```java
public interface PlcDriver {                       // 프로토콜마다 Spring 빈 하나
    String protocol();                             // "MELSEC_MC" …
    void validateSettings(JsonNode settings) throws ToolControlException;      // 커넥터 등록·수정 때
    PlcAddress parseAddress(String address, PointType type, int count, int length) throws ToolControlException; // 인터페이스 등록 때
    PlcSession open(PlcConnectorInfo connector, PlcSessionListener listener) throws ToolControlException;       // 게시 때
}

public interface PlcSession {                      // 지금의 ProgrammableControllerAdapter 자리 — 연결·재연결·상태
    PlcResult read(List<PlcPoint> points, Deadline deadline) throws ToolControlException;
    PlcResult write(List<PlcPoint> points, Map<String, Object> values, Deadline deadline) throws ToolControlException;
    AdapterStatus status();
    void close();             // 이벤트 내고 닫기(해제)
    void closeQuietly();      // 앱 종료
    default boolean isLocal() { return true; }
}
```

- 연결 관리(채널·재연결·시간 초과·늦은 응답 버림·이벤트)는 지금 `MelsecProgrammableControllerAdapter` 가 가진 것을 **공통 `ChannelPool`** 로 떼어 MELSEC·Modbus 가 같이 쓴다(둘 다 TCP 요청·응답, 포트당 연결). OPC UA 는 라이브러리(Eclipse Milo) 세션을 쓴다.
- 값 변환은 **`ValueCodec`** — 형·바이트 순서·워드 순서만 알고 프로토콜은 모른다. MELSEC(리틀·워드 단위), Modbus(빅·레지스터 단위)가 같은 코덱을 설정만 달리 쓴다. OPC UA 는 형 붙은 값(Variant)을 바로 변환.
- `RAW` 모드는 지금의 `MelsecPlcRequest/Response` 를 MELSEC 드라이버 안에 그대로 두고 `PlcSession.exchangeRaw(byte[])` 로만 연다(호환 모드 전용).
- 커맨드 확장 지점(`beforeExchange`·`afterExchange`)은 유지하되 인자가 `PlcOperation`·`PlcResult` 로 바뀐다. 레거시 커스텀 커맨드 클래스는 지금 없음을 확인하고(운영 DB `CMD_CLASS_NAME`) 바꾼다.

## 6. 프로토콜별 요점

### 6.1 MELSEC MC (3E·4E)

| 항목 | 내용 |
|---|---|
| 주소 | 디바이스 기호 + 번호. 10진(D, M, R, ZR…)·16진(X, Y, B, W…) 디바이스 표를 드라이버가 가진다. 디바이스 코드(BINARY 1바이트·ASCII 2문자)는 표에서 |
| 명령 | 워드 일괄 읽기·쓰기(0401·1401, 서브 0000), 비트 디바이스는 서브 0001. 랜덤 읽기(0403)는 나중 |
| 값 | 리틀엔디안, 32비트는 연속 두 워드(하위 워드 먼저), 문자열은 워드당 2바이트 |
| 프레임 | 3E BINARY·ASCII(M1 고침), **4E**(#12 — 시리얼 번호로 응답을 요청과 맞춘다. 늦은 응답을 버릴 때 연결을 끊지 않아도 된다) |
| 응답 검증 | 프레임의 응답 데이터 길이(`respDataLen`)와 실제 길이, 종료 코드(#12, M4) |
| 연결 확인 | 주기적 **루프백 시험(0619)** 으로 반쯤 죽은 연결을 찾는다(#12 "주기적 연결 상태 확인"). 응답이 없으면 그 채널을 닫고 재연결 |

### 6.2 Modbus TCP

| 항목 | 내용 |
|---|---|
| 주소 | 영역 + 번호: 코일(`CO`, FC1·5·15), 입력 접점(`DI`, FC2), 입력 레지스터(`IR`, FC4), 보유 레지스터(`HR`, FC3·6·16). 5·6자리 관례 번호(40001)와 영역+0 기준(`hr:0`)을 모두 받고 `addressBase` 로 맞춘다 |
| 값 | 레지스터 16비트 빅엔디안. 32·64비트의 **워드 순서는 장비마다 달라** 커넥터 기본값 + 점별로 바꾼다 |
| 프레임 | MBAP 헤더의 트랜잭션 ID 로 응답을 요청과 맞춘다(4E 와 같은 이점) |
| 오류 | 예외 응답(함수 코드 + 0x80, 예외 코드)을 `plcStatus` 로 |
| 채널 | 장비가 동시 연결 수를 제한하는 경우가 많다 — 포트당 연결 1개, 요청은 채널에서 차례로(지금 MELSEC 과 같음) |

### 6.3 OPC UA (2차)

| 항목 | 내용 |
|---|---|
| 주소 | 노드 ID 문자열(`ns=2;s=…`, `ns=3;i=1001`). 이름공간 URI 표기(`nsu=…;s=…`)도 받는다 |
| 값 | 서버가 형을 붙여 준다 — 점의 `type` 은 검증·변환용. 배열은 그대로 |
| 연결 | 세션·보안 채널(Eclipse Milo 클라이언트). 채널(포트·VIP) 개념이 없고 세션 하나에 요청을 병렬로 보낸다 |
| 오류 | 서비스·점별 StatusCode |
| 구독 | 3차 — 모니터 항목을 PUSH 인터페이스(`InterfaceType.PUSH`, 지금 쓰는 곳 없음)로 받아 전달 경로에 태운다 |
| 다중화 | 세션은 주인 인스턴스만 연다(임대 그대로). 다른 인스턴스는 작업 단위로 넘긴다(§7) |

## 7. 다중화 전달 계약 바꾸기

지금 `/internal/plc/{name}/exchange` 는 프레임 바이트를 넘긴다. OPC UA 에는 넘길 바이트가 없고, Modbus·MELSEC 도 점 묶기·응답 검증을 주인이 해야 한다. **작업 단위로 바꾼다.**

```
POST /internal/plc/{name}/execute
{ "op": "READ"|"WRITE"|"RAW", "points": [...], "values": {...}, "frame": "<base64, RAW 만>", "reqId": "...", "timeoutMs": 3000 }
→ 200 { "values": {...}, "status": {...}, "frame": "<base64, RAW 만>" } | 409 NOT_OWNER | 503 | 504
```

- 서명·포트·409 재시도·WRITE 재전송 금지 규칙은 그대로(plc-ha-design.md §4).
- 점 정의를 함께 보내 주인이 인터페이스를 다시 읽지 않는다(인스턴스 사이 설정이 잠깐 달라도 요청을 받은 쪽 정의대로).
- 순차 재기동 중 두 버전이 섞이므로 받는 쪽은 한동안 `/exchange` 도 받는다.

## 8. 이전 (지금 운영 정의)

| 대상 | 방법 |
|---|---|
| 커넥터 | migration: `PROTOCOL='MELSEC_MC'`, `SETTINGS_JSON` 에 프레임 형식·헤더 템플릿 값. 옛 칼럼은 남긴다(되돌리기용) |
| 인터페이스 | 그대로 `MODE='RAW'` 로 돈다 — 동작이 바뀌지 않는다 |
| RAW → POINTS 변환 | **변환 도구**(관리 API `POST /api/v1/ifsvc/management/{id}/convert-to-points`): 헤더에서 명령(0401·1401)·디바이스 코드·시작 주소·워드 수를 읽고, 응답 인터페이스의 데이터 속성(이름·형·길이)으로 점을 만든다. 알아볼 수 없으면 거절하고 사유를 돌려준다. 미리보기(`dryRun`) 후 적용, 설정 변경 이력에 남는다 |
| 응답 인터페이스 | POINTS 로 바꾼 인터페이스의 응답 인터페이스는 쓰는 곳이 없으면 정리(삭제는 운영자가) |
| 외부 계약 | 요청·응답 JSON(`data` 키·값)은 바뀌지 않는다 — 점 이름을 옛 데이터 속성 이름과 같게 만든다. 값의 형은 같다(INT16 ↔ SHORT) |

## 9. 시험

| 무엇 | 어떻게 |
|---|---|
| 코덱·주소 파서 | 단위 — 형·바이트·워드 순서 표, MELSEC 디바이스 표, Modbus 영역·기준 |
| MELSEC | vplc(MC 3E) e2e 그대로 + POINTS 인터페이스 + RAW→POINTS 변환 뒤 같은 값 + 4E·루프백(vplc 확장 필요 — §10 P6) |
| Modbus TCP | e2e 용 가짜 장비 — 파이썬 Modbus 서버(`fake_plc.py` 와 같은 자리). 워드 순서·예외 응답·연결 수 제한 |
| OPC UA | e2e 용 서버(파이썬 `asyncua` 또는 Milo 예제 서버) |
| 다중화 | `test_plc_remote.py` 를 `/execute` 로 — 세 프로토콜 |

## 10. 결정할 것

| # | 질문 | 제안 | 이유 |
|---|---|---|---|
| P1 | 지금 RAW 인터페이스 | **호환 모드로 계속 돌리고 변환 도구로 하나씩 옮긴다** | 일괄 자동 변환은 알아보지 못하는 정의에서 운영이 깨진다. 외부 계약은 같아 급하지 않다 |
| P2 | 점 저장 | **표 `INTERFACE_POINT`**(인터페이스 ID, 순서, 이름, 주소, 형, 개수, 길이, 순서 재정의) | 화면이 행 단위로 편집하고, 같은 주소를 쓰는 인터페이스 찾기(검색)가 쉽다. JSON 칼럼은 검색이 어렵다 |
| P3 | 주소 표기 | **프로토콜별 문자열**(`D100`, `HR40001`, `ns=2;s=…`) + 드라이버 검증 | 현장 문서·PLC 도구의 표기와 같다. 화면은 드라이버가 주는 디바이스 목록으로 도움말만 |
| P4 | 다중화 계약 | **작업 단위 `/execute`** (`/exchange` 는 전환 기간만) | OPC UA 를 담을 수 없는 계약을 미리 바꾼다 |
| P5 | 응답 상태 | **`plcStatus` 추가, `resultCode` 유지** | 외부 시스템이 지금 `resultCode` 를 본다 |
| P6 | 시험 장비 | **Modbus·OPC UA 는 e2e 파이썬 가짜 장비, vplc 는 MELSEC(4E·루프백 추가)만** | vplc 를 프로토콜마다 키우면 시험 장비가 제품만큼 커진다. 화면 시연용 Modbus 시뮬레이터가 필요해지면 그때 vplc 에 |
| P7 | OPC UA 시점 | **2차(프론트와 병행)**, 1차는 모델이 담는지만 확인 | 라이브러리·보안 설정이 무겁고, 화면은 1차 모델로 만들 수 있다 |
| P8 | 쓰기 뒤 응답 | **기본은 빈 `data`, 인터페이스 선택 `readBack`** | 쓰기 확인이 필요한 곳만 한 번 더 읽는다 |
| P9 | 커넥터 `settings` 저장 | **JSON 칼럼 `SETTINGS_JSON`**, 비밀값(OPC UA 비밀번호)은 암호화 | 프로토콜마다 필드가 달라 표로 나누면 프로토콜 수만큼 표가 는다. 검색할 일이 없다 |
| P10 | 변경 감지 | **값(`data`) 정규 JSON 비교**(RAW 는 지금처럼 바이트) | 프로토콜과 무관하고, 묶기 방식이 바뀌어도 같다 |

## 11. 순서

1. **모델·드라이버 경계 + MELSEC 이식**: `PlcDriver`·`PlcSession`·`ChannelPool`·`ValueCodec`, MELSEC 주소 파서·블록 묶기, POINTS 인터페이스·API(`INTERFACE_POINT`), 응답 `plcStatus`, 값 기반 변경 감지, 결함 M1~M3, RAW 호환. 커넥터 `protocol`·`settings` migration.
2. **MELSEC 보강(#12)**: 4E 프레임, 응답 길이 검증(M4), 주기적 루프백, 포트 여러 개 병렬 연결 시험. vplc 4E·루프백.
3. **Modbus TCP**: 드라이버·e2e 가짜 장비.
4. **다중화 계약 `/execute`**, 변환 도구(RAW → POINTS).
5. **API 계약 고정 → 프론트 재작성**(커넥터·인터페이스 화면은 이 모델로). 시험 읽기 API(`POST /api/v1/plc-connector/{name}/test-read`, 점 목록을 받아 바로 읽기)를 화면용으로 둔다.
6. **OPC UA**(2차, 프론트와 병행) → 구독(3차).

각 단계는 운영 중 MELSEC 정의(RAW)가 그대로 도는 상태로 끝난다 — 배포 사이에 끊김 없이 넘어갈 수 있게.
