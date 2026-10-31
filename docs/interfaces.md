# 인터페이스 정의 — 점(point)

> 작성일: 2026-10-31 (v0.4.0). 대상: tc 인터페이스(opcode) 정의.
> 관련: [plc-protocol-design.md](plc-protocol-design.md) §4(데이터 모델), [interface-detail-design.md](interface-detail-design.md)(인터페이스 settings·요청 미리보기)

인터페이스는 타 시스템이 부르는 opcode 하나이고, **어느 PLC 의 어느 값(점)을 읽거나 쓰는가**를 정의한다. PLC 에 보낼 프레임(명령·디바이스 코드·시작 주소·길이)은 사람이 적지 않는다 — 프로토콜 드라이버가 점에서 만든다.

> 예전에는 MELSEC 3E 프레임 바이트를 헤더·데이터 속성으로 적는 정의(RAW)도 있었다. 운영 중인 시스템이 없어 2026-10-31 에 없앴다(결정 I7, [interface-detail-design.md](interface-detail-design.md) §6). 지금은 정의 방식이 하나다.

## 1. 인터페이스

| 필드 | 내용 |
|---|---|
| `name` | opcode — 영숫자·`_` `.` `-` |
| `plcName` | PLC 커넥터. 커넥터의 프로토콜(MELSEC MC·Modbus TCP, 계획 OPC UA)이 주소 문법과 프레임을 정한다 |
| `interfaceType` | `READ`(점을 읽어 돌려줌) · `WRITE`(요청 값을 점에 씀) |
| `points` | 점 목록(아래) |
| `readBack` | WRITE — 쓴 뒤 같은 점을 다시 읽어 응답 `data` 에 넣는다 |
| `settings` | 이 인터페이스에만 쓸 프로토콜 설정(커넥터 값 덮어쓰기·전용 옵션) — 비우면 커넥터 설정 그대로 |
| `use`·`description` | 사용 여부, 설명 |

## 2. 점

점 하나는 **PLC 의 한 주소에 있는, 이름 붙인 값 하나(또는 같은 형의 배열)** — 실제로 읽고 쓰는 데이터다.

| 필드 | 내용 | 예 |
|---|---|---|
| `name` | 요청·응답 `data` 의 키 | `temp` |
| `address` | 프로토콜별 주소 — MELSEC `D100`·`M10`·`X1F`(16진)·`ZR20000`, Modbus `HR1`·`40001`·`IR10`·`CO1`·`DI5` | `D5004` |
| `type` | `BOOL`·`INT16`·`UINT16`·`INT32`·`UINT32`·`INT64`·`FLOAT32`·`FLOAT64`·`STRING`·`BYTES` | `FLOAT32` |
| `count` | 배열 길이(기본 1) — 값이 JSON 배열 | `5` |
| `length` | `STRING`·`BYTES` 의 바이트 수 | `16` |
| `byteOrder`·`wordOrder` | 이 점만 커넥터 기본 순서를 바꿀 때 | `LITTLE` |

- **READ**: 점들을 읽어 `data{이름: 값}` 으로 돌려준다.
- **WRITE**: 요청 `data` 에서 점 이름의 값을 꺼내 쓴다(빠지면 400).
- 드라이버는 **가까운 점끼리 한 요청으로 묶는다**(같은 디바이스·영역, 빈 칸 8워드까지 — 인터페이스 settings `mergeGap` 으로 바꾼다).
- 응답에는 `plcStatus`(MELSEC 종료 코드·Modbus 예외 코드)가 함께 온다.
- 동기·비동기는 부르는 쪽이 API 로 고른다 — `/request-and-reply`(결과를 응답 본문으로), `/request`(접수만, 결과는 callback·전달 정의로). 결과를 WebHook·DB 표로 보내는 것은 인터페이스가 아니라 **전달 정의**(전달 화면)가 정한다.
- 실제로 보낼 요청은 화면의 **요청 미리보기**(등록·수정 대화상자, 상세 보기) 또는 `POST /api/v1/ifsvc/management/plan` 으로 본다.

## 3. 프로토콜별로

| 커넥터 프로토콜 | 프레임 | 비고 |
|---|---|---|
| MELSEC MC | 커넥터 `frame` — `3E_BINARY`(기본)·`3E_ASCII`·`4E_BINARY`·`4E_ASCII` | Q·L·iQ-R 이더넷 모듈. 다른 국·네트워크 PLC 로 중계는 인터페이스 settings `networkNo`·`stationNo`. **1E 프레임(A 호환)만 받는 오래된 장비는 아직 안 된다** — 필요하면 드라이버에 1E 를 더한다 |
| Modbus TCP | FC 1·2·3·4(읽기), 5·6·15·16(쓰기) | 게이트웨이 뒤 장치는 인터페이스 settings `unitId`, 쓰기 함수 고정은 `writeFunction` |
| OPC UA | (계획 D단계) | 주소 = NodeId |

## 4. 예제 (로컬 tc, 커넥터 `plc-e2e` — 가상 PLC `vplc-e2e` 의 D 메모리)

e2e 시험이 쓰는 D100~D300 대와 겹치지 않게 D5000 대에 둔다. 이름은 `ex.` 로 시작한다.

| opcode | 타입 | 점 | 보여 주는 것 |
|---|---|---|---|
| `ex.line1.status.read` | READ | `run` D5000 INT16, `speed` D5001 UINT16, `count` D5002 INT32, `temp` D5004 FLOAT32 | 여러 형, 이어진 점이 요청 하나로 |
| `ex.line1.recipe.read` | READ | `recipeNo` D5100 INT16, `recipeName` D5110 STRING(16), `setpoints` D5120 INT16 ×5 | 문자열·배열, 빈 칸이 8워드를 넘으면 요청이 나뉨 |
| `ex.line1.command.write` | WRITE(+ 다시 읽기) | `cmd` D5200 INT16, `target` D5201 INT32 | 쓰기 — 요청 `data` 의 점 이름 값 |
| `ex.line1.alarm.read` | READ, settings `monitoringTimer=32`·`mergeGap=0` | `alarm1` D5300 UINT16, `alarm2` D5302 UINT16 | 인터페이스 settings — 빈 칸 하나여도 요청 둘 |

부르는 예(`request-and-reply`, API 키 또는 관리자 토큰):

```json
POST /api/v1/ifsvc/request-and-reply
{ "transactionId": "t-1", "messageId": "t-1", "opcode": "ex.line1.command.write", "sender": "mes", "receiver": "tc",
  "data": { "cmd": 1, "target": 1500 } }
```

응답(다시 읽기 켜짐):

```json
{ "success": true, "data": { "cmd": 1, "target": 1500 }, "plcStatus": { "protocol": "MELSEC_MC", "code": 0, "text": "OK" }, "resultCode": 0, "...": "..." }
```
