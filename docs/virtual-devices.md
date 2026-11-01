# 가상 장비 — Modbus TCP · OPC UA

> 작성일: 2026-11-01 (v0.4.0). 대상: `wow-tc-backend/vplc-modbus`, `wow-tc-backend/vplc-opcua`.
> 관련: [opcua-design.md](opcua-design.md), [plc-protocol-design.md](plc-protocol-design.md), e2e `test_plc_vmodbus.py`·`test_plc_opcua.py`

tc 의 Modbus·OPC UA 드라이버를 개발·시험할 상대 장비다. MELSEC 가상 PLC(`vplc`)처럼 Gradle 서브프로젝트로 두었지만 더 가볍게 만들었다.

| | vplc(MELSEC) | vplc-modbus | vplc-opcua |
|---|---|---|---|
| 프로토콜 | MC 3E·4E | Modbus TCP | OPC UA(None·Basic256Sha256, 익명·사용자 이름) |
| 장비 포트 | 15000 … | 15020 | 4840 (`/vplc`) |
| REST | 8088 | **8091** | **8092** |
| DB | MariaDB(장비·이력) | 없음(메모리) | 없음(메모리) |
| 사내 프레임워크 | 씀 | 안 씀 | 안 씀 |
| 화면 | tc 화면의 가상 PLC 메뉴 | tc 화면의 가상 PLC 메뉴(목록·메모리) | tc 화면의 가상 PLC 메뉴(목록·태그) |

REST 포트는 8089·8090 이 이 PC 에서 다른 앱(옛 vplc, mes-dev 서비스)과 겹쳐 8091·8092 로 잡았다. 환경 변수 `VPLC_MODBUS_HTTP_PORT`·`VPLC_OPCUA_HTTP_PORT` 로 바꾼다.

tc 화면(가상 PLC 메뉴, docs/frontend-design.md §16.1)이 REST 를 바로 부른다 — `tc-config.json` 의 `vplcModbusUrl`·`vplcOpcuaUrl`, CORS 허용 출처는 `VPLC_CORS_ALLOWED_ORIGIN_PATTERNS`(기본 5180·8087).

## 1. 띄우기

```bash
cd wow-tc-backend
./gradlew :vplc-modbus:bootRun
./gradlew :vplc-opcua:bootRun
```

둘 다 기본으로 `127.0.0.1` 에만 연다(바깥에서 붙지 않게) — `VPLC_MODBUS_BIND`·`VPLC_OPCUA_BIND` 로 바꾼다. 다시 띄우면 값은 처음 값으로 돌아간다.

## 2. vplc-modbus

장치 = 포트 + 단위 ID. 한 포트에 단위 ID 가 다른 장치를 여럿 두면 **게이트웨이 뒤 장치들**처럼 동작한다(없는 단위 ID 는 예외 0x0B).

**기본 장치**(`application.yml` `vplc-modbus.devices`)

| 이름 | 포트 | 단위 ID | 칸 |
|---|---|---|---|
| `mb-e2e` | 15020 | 1 | 10000 |
| `mb-e2e-2` | 15020 | 2 | 1000 |

**처리하는 함수** — FC 1·2·3·4(읽기), 5·6·15·16(쓰기). 모르는 함수 01, 범위 밖 02, 개수·값이 틀림 03(실제 장치와 같은 한도: 읽기 비트 2000·레지스터 125, 쓰기 비트 1968·레지스터 123).

**REST** (`/api/v1/modbus/devices`, 번호는 프로토콜 주소 0부터 — tc 의 `HR1`(addressBase 1)은 `hr` 0 번)

| 요청 | 내용 |
|---|---|
| `GET /` | 장치 목록(바인드 주소·지연·무응답·연결 수) |
| `POST /` `{name, port, unitId, size, delayMs}` | 장치 더하기(그 포트가 처음이면 서버를 연다) |
| `DELETE /{name}` | 지우기(그 포트의 마지막 장치면 서버를 닫는다) |
| `GET /{name}/{co\|di\|ir\|hr}?start=&count=` | 값 보기 — 비트는 true/false, 레지스터는 0~65535 |
| `PUT /{name}/{co\|di\|ir\|hr}` `{start, values}` | 값 넣기 — 입력 영역(DI·IR)도 여기로 바꾼다 |
| `PUT /{name}/behavior` `{delayMs, silent}` | 늦게 답하기·답하지 않기(tc 시간 초과 시험) |
| `POST /{name}/drop-connections` | 그 포트의 연결 모두 끊기(tc 재연결 시험) |

## 3. vplc-opcua

Eclipse Milo 1.1.8 서버. 네임스페이스 `urn:wow:tc:vplc-opcua` — 앞에 빈 네임스페이스(`…:extra`)를 하나 더 두어 **번호가 3** 이다(`ns=2` 를 가정한 코드를 잡으려고). 태그 경로 `Line1.Temp` → 폴더 `Line1`(Objects 아래) + 변수 `Temp`, NodeId `nsu=urn:wow:tc:vplc-opcua;s=Line1.Temp`(= `ns=3;s=Line1.Temp`).

**보안**(docs/opcua-design.md §7.2) — 엔드포인트 None·Basic256Sha256(Sign·SignAndEncrypt), 각각 익명·사용자 이름. 서버 인증서는 기동 때 자체 서명(메모리 — 다시 뜨면 바뀐다: tc 의 TOFU 커넥터는 거절하므로 "서버 인증서 잊기"). tc 클라이언트 인증서는 기본으로 모두 믿는다(`VPLC_OPCUA_TRUST_ALL_CLIENTS`). 시험 사용자 `tc-e2e` / 비밀번호 `VPLC_OPCUA_PASSWORD`(기본 `tc-e2e-pass` — 127.0.0.1 시험 장비라 기본값을 둔다). `VPLC_OPCUA_REQUIRE_LOGIN=true` 면 익명을 거절한다.

**태그 형** — tc 점 형과 같은 이름: BOOL·INT16·UINT16·INT32·UINT32·INT64·FLOAT32·FLOAT64·STRING.

**흉내(simulation)** — `kind` RAMP(최소→최대 1씩 반복)·SINE·TOGGLE(BOOL)·RANDOM, `periodMs` 마다. tc 구독(SUBSCRIBE) 시험에 쓴다.

**기본 태그**(`application.yml` `vplc-opcua.tags`)

| 경로 | 형 | 값·흉내 |
|---|---|---|
| `Line1.Temp` | FLOAT64 | 25.5 |
| `Line1.Count` | INT32 | 0 |
| `Line1.Speed` | UINT16 | 1500 |
| `Line1.Running` | BOOL | true |
| `Line1.Recipe` | STRING | R-01 |
| `Line1.Raw` | INT16 | 253(배율 0.1 시험) |
| `Line1.Counter` | INT32 | RAMP 1초 |
| `Line1.Wave` | FLOAT64 | SINE 0.5초 |
| `Line1.Blink` | BOOL | TOGGLE 2초 |
| `Line2.Temp` | FLOAT32 | 18.5 |

**REST** (`/api/v1/opcua`)

| 요청 | 내용 |
|---|---|
| `GET /` | 엔드포인트·네임스페이스 번호·태그 수·세션 수 |
| `GET /tags` | 태그 목록(NodeId·형·지금 값·흉내) |
| `POST /tags` `{path, type, value, simulation, description}` | 태그 더하기(폴더는 경로대로 만든다) |
| `GET /tags/{path}` · `PUT /tags/{path}` `{value}` | 값 보기·바꾸기(흉내 중인 태그는 다음 주기에 다시 흉내 값) |
| `DELETE /tags/{path}` | 태그 지우기(폴더는 둔다) |

## 4. tc 에서 쓰기

| 커넥터 | 프로토콜 | 주소·포트 | 설정 |
|---|---|---|---|
| Modbus | `MODBUS_TCP` | 127.0.0.1 : 15020 | `unitId` 1(인터페이스 settings `unitId` 2 로 두 번째 장치) |
| OPC UA | `OPC_UA` | 127.0.0.1 : 4840 | `endpointPath` `/vplc` — 점은 인터페이스 폼의 **노드 찾아보기**로 고른다. 보안 시험은 `securityPolicy` BASIC256SHA256 + `authMode` USERNAME(`tc-e2e`, 비밀번호는 커넥터 비밀번호 칸) |

## 5. 시험

- 단위: `./gradlew :vplc-modbus:test :vplc-opcua:test` — Modbus PDU·TCP 왕복(단위 ID·게이트웨이 예외·무응답), OPC UA 클라이언트로 읽기·쓰기·찾아보기·흉내.
- e2e(tc 상대): `test_plc_vmodbus.py`(8), `test_plc_opcua.py`(30, 보안 포함) — 장비가 떠 있지 않으면 건너뛴다.
