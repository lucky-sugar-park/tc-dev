# 가상 PLC — MELSEC MC · Modbus TCP · OPC UA (한 vplc 안)

> 작성일: 2026-11-01 (v0.4.0), 같은 날 vplc 로 합침. 대상: `wow-tc-backend/vplc`.
> 관련: [opcua-design.md](opcua-design.md), [plc-protocol-design.md](plc-protocol-design.md), [frontend-design.md](frontend-design.md) §16, e2e `test_plc_vmodbus.py`·`test_plc_opcua.py`

tc 의 드라이버를 개발·시험할 상대 장비다. **vplc 앱 하나가 세 종류의 가상 PLC 를 여러 대씩 띄운다.** 셋 모두 VPLC 테이블에 등록되고 같은 상태 단계를 따른다.

| | MELSEC MC | Modbus TCP | OPC UA |
|---|---|---|---|
| 종류(`protocol`) | `MELSEC_MC`(비어 있으면 이것) | `MODBUS_TCP` | `OPC_UA` |
| 한 대 = | 포트 여러 개(시작 포트·포트 수) | 포트 + 단위 ID | 엔드포인트 `opc.tcp://IP:포트/경로` |
| 메모리 | MC 메모리 종류(D·M·W…) | 영역 HR·IR·CO·DI(영역마다 `size` 칸) | 태그(정의는 DB `VPLC_OPCUA_TAG`) |
| 설정(`settings`) | — | `unitId`(1)·`size`(10000)·`delayMs`(0) | `path`(/vplc)·`requireLogin`(false)·`trustAllClients`(true) |
| 일시정지 | 연결은 두고 답하지 않음 | 같음(그 단위 ID 만) | 값 읽기·쓰기를 재개까지 붙잡음(최대 120초) |
| 연결 이력 | Netty 채널 | 소켓 연결(그 포트에 붙은 장치마다) | 세션(클라이언트 주소·세션 아이디) |

## 1. 결정

| # | 결정 | 이유 |
|---|---|---|
| V1 | 세 종류를 **vplc 한 앱**에 둔다 — 따로 뜨던 `vplc-modbus`(:8091)·`vplc-opcua`(:8092)의 엔진을 vplc 로 옮겼다 | 등록·게시·시작·정지·해제, 연결 이력, 화면 메뉴가 종류마다 달랐다. 한 표·한 API 로 관리한다 |
| V2 | VPLC 테이블에 `PROTOCOL`·`SETTINGS`(JSON) 칸. 예전 줄은 `PROTOCOL` 이 비어 MELSEC | ddl-auto 가 칸을 만든다(vplc DB). 종류별 칸을 따로 두지 않고 설정 JSON 으로 |
| V3 | 종류는 등록 때 정하고 바꾸지 않는다(수정에서 바꾸면 400) | 메모리 모양이 달라 바꾸면 게시된 객체와 맞지 않는다 |
| V4 | Modbus·OPC UA 는 포트 하나, 프레임은 쓰지 않아 BINARY, 메모리 종류는 고정(HR·IR·CO·DI / TAG), 포트당 연결 기본 8 | 등록 때 vplc(`VirtualDeviceFactory.normalize`)가 맞춘다 |
| V5 | 한 포트에 단위 ID 가 다른 Modbus 가상 PLC 를 여럿 — 첫 장치가 시작하면 포트를 열고 마지막이 정지하면 닫는다. 같은 포트·단위 ID 는 하나만(409). 포트당 연결 한도는 붙은 장치 중 가장 큰 값 | 게이트웨이 뒤 장치들 흉내(tc 인터페이스 settings `unitId`) |
| V6 | OPC UA 태그 정의는 DB(`VPLC_OPCUA_TAG`), 지금 값은 메모리 — 정지했다 시작하면 처음 값 | MELSEC 메모리와 같은 수명. 태그 정의는 다시 띄워도 남아야 한다 |
| V7 | OPC UA 로그인 사용자는 vplc 설정 `vplc.opcua.users`(모든 OPC UA 가상 PLC 공통, 기본 `tc-e2e` / `VPLC_OPCUA_PASSWORD`, 기본값 `tc-e2e-pass`) | 시험 장비 — 비밀번호를 DB 에 두지 않는다 |
| V8 | 동작(시작·정지·일시정지·재개)은 장치에 먼저 하고 된 뒤 DB 상태를 바꾼다 | 예전에는 DB 를 먼저 RUNNING 으로 바꿔, 포트를 열지 못해도 RUNNING 으로 남았다 |
| V9 | 게시된 채로 지우면 먼저 메모리에서 내리고(포트 닫기), OPC UA 태그도 지운다 | 예전에는 지워도 포트가 열린 채 남았다 |
| V10 | `vplc-modbus`·`vplc-opcua` 프로젝트는 **확인될 때까지 남긴다**(사용자 요청). 같은 포트(15020·4840)를 쓰므로 vplc 와 함께 띄우지 않는다 | 나중에 지운다 |

## 2. 띄우기

```bash
cd wow-tc-backend
./gradlew :vplc:bootJar
java -jar vplc/build/libs/vplc-0.4.0.jar --spring.config.import=optional:file:vplc/src/main/resources/application-local.yml
```

기동 때 DB 의 가상 PLC 중 게시·실행 상태였던 것을 다시 올린다(RUNNING 이던 것은 시작). OPC UA 서버 인증서는 시작할 때마다 새로 만든다(메모리) — tc 의 TOFU 커넥터는 거절하므로 "서버 인증서 잊기".

## 3. REST (`/api/v1/vplc`)

공통(예전 그대로) — `management/register·update·delete·find·search·publish·release`, `control/start·stop·pause·resume/{id}`, `management/search/connection/{id}`. 등록 본문에 `protocol`·`settings`.

| 요청 | 종류 | 내용 |
|---|---|---|
| `POST /control/drop-connections/{id}` | 모두 | 붙은 연결을 끊는다(포트는 계속 받는다) — tc 재연결 시험. `{dropped}` |
| `POST /manipulation/read·write·clear/all` | MELSEC | MC 메모리(예전 그대로). 다른 종류면 400 |
| `GET /modbus/{id}/{co\|di\|ir\|hr}?start=&count=` | Modbus | 값 — 비트 true/false, 레지스터 0~65535. 번호는 프로토콜 주소(0부터 — tc 의 `HR1` 은 `hr` 0) |
| `PUT /modbus/{id}/{co\|di\|ir\|hr}` `{start, values}` | Modbus | 값 넣기 — 입력 영역(DI·IR)도 |
| `POST /control/delay/{id}` `{delayMs}` | Modbus | 응답 지연 — 메모리에 바로, 설정에도 |
| `GET /opcua/{id}` | OPC UA | 엔드포인트·네임스페이스 URI·번호(실행 중일 때, 아니면 -1)·세션 수·태그 수 |
| `GET /opcua/{id}/tags` · `POST` `{path, type, value, simulation, description}` | OPC UA | 태그 목록(실행 중이면 지금 값) · 더하기(DB 에 남고, 실행 중이면 서버에도) |
| `PUT /opcua/{id}/tags/{path}` `{value}` · `DELETE` | OPC UA | 지금 값(실행 중일 때만) · 지우기 |

게시 안 된 가상 PLC 의 메모리를 건드리면 409, 없는 아이디의 제어는 404(예전과 같다).

## 4. Modbus

**처리하는 함수** — FC 1·2·3·4(읽기), 5·6·15·16(쓰기). 모르는 함수 01, 범위 밖 02, 개수·값이 틀림 03(실제 장치와 같은 한도: 읽기 비트 2000·레지스터 125, 쓰기 비트 1968·레지스터 123). 그 포트에 없는(실행 중이 아닌) 단위 ID 는 게이트웨이 예외 0x0B.

"연결 수"는 그 포트의 연결이다 — 같은 포트의 장치들이 같은 연결을 이력에 남긴다.

## 5. OPC UA

Eclipse Milo 1.1.8 서버. 네임스페이스 `urn:wow:tc:vplc-opcua` — 앞에 빈 네임스페이스(`…:extra`)를 하나 더 두어 **번호가 3** 이다(`ns=2` 를 가정한 코드를 잡으려고). 태그 경로 `Line1.Temp` → 폴더 `Line1`(Objects 아래) + 변수 `Temp`, NodeId `nsu=urn:wow:tc:vplc-opcua;s=Line1.Temp`(= `ns=3;s=Line1.Temp`).

**보안**(docs/opcua-design.md §7.2) — 엔드포인트 None·Basic256Sha256(Sign·SignAndEncrypt), 각각 익명·사용자 이름. `requireLogin` 이면 익명 거절, `trustAllClients`(기본)면 tc 클라이언트 인증서를 모두 믿는다.

**태그 형** — tc 점 형과 같은 이름: BOOL·INT16·UINT16·INT32·UINT32·INT64·FLOAT32·FLOAT64·STRING. **흉내** — `kind` RAMP(최소→최대 1씩 반복)·SINE·TOGGLE(BOOL)·RANDOM, `periodMs` 마다. tc 구독(SUBSCRIBE) 시험에 쓴다.

## 6. tc e2e 가상 PLC

e2e(`common.ensure_vplc`)가 없으면 등록하고 게시·시작한다.

| 이름 | 종류 | 주소 | tc 커넥터 |
|---|---|---|---|
| `vplc-e2e` | MELSEC | 127.0.0.1:15000 | `plc-e2e` |
| `mb-e2e` | Modbus 단위 1, 10000칸 | 127.0.0.1:15020 | `plc-modbus-vplc`·`plc-vmodbus-e2e` |
| `mb-e2e-2` | Modbus 단위 2, 1000칸 | 127.0.0.1:15020 | 인터페이스 settings `unitId` 2 |
| `ua-e2e` | OPC UA, 태그 10개(`common.E2E_OPCUA_TAGS`) | opc.tcp://127.0.0.1:4840/vplc | `plc-opcua-vplc`·`plc-opcua-e2e` |

## 7. 시험

- 단위 `./gradlew :vplc:test`(18) — MC(`McResponderTest`·`MemoryTest`), Modbus PDU·TCP 왕복(`ModbusSimulatorTest`), 한 포트 두 장치·일시정지·마지막 정지 때 포트 닫기·설정 검사(`ModbusVirtualDeviceTest`), OPC UA 서버·보안(`VirtualOpcUaServerTest`·`VirtualOpcUaSecurityTest`), 시작·일시정지 동안 읽기 무응답·태그·정지 뒤 같은 포트로 다시 시작(`OpcUaVirtualDeviceTest`).
- e2e `test_vplc_http_methods.py`(35), `test_plc_vmodbus.py`(8), `test_plc_opcua.py`(30). Playwright `vplc.spec.ts`(5).
