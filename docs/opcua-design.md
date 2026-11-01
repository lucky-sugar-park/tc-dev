# OPC UA 커넥터 설계 (인터페이스 세부 정의 D단계)

> 작성일: 2026-11-01 (v0.4.0). 상태: **D1·D2·D3 구현됨**(§6, §7.5). 가상 서버는 [virtual-devices.md](virtual-devices.md).
> 관련: [interface-detail-design.md](interface-detail-design.md)(결정 I4 — 구독 결과는 전달 정의 경로), [plc-protocol-design.md](plc-protocol-design.md)(드라이버 경계), [plc-ha-design.md](plc-ha-design.md)(소유권·원격 어댑터), [interfaces.md](interfaces.md)

## 1. 왜 다른가

MELSEC·Modbus 는 **요청 프레임 하나 ↔ 응답 프레임 하나**다. 그래서 드라이버는 상태 없이 프레임만 만들고, 연결은 TCP 어댑터(`TcpPlcAdapter`)가 맡았다.

OPC UA 는 **세션**이다 — 보안 채널을 열고 세션을 만든 뒤 Read·Write·Browse·Subscription 서비스를 부른다. 메시지 조립·암호화·세션 유지·재연결은 라이브러리(Eclipse Milo)가 한다. tc 가 프레임 바이트를 만들 일이 없다.

그래서 OPC UA 는 **어댑터가 Milo 클라이언트를 들고 작업(`PlcOperation`)을 직접 실행**한다. 나머지 경계는 그대로다.

| 그대로 쓰는 것 | 설명 |
|---|---|
| 인터페이스·점 정의 | 주소 칸에 NodeId, 형·개수, 배율·단위·쓰기 범위(C단계)도 같다 |
| `PlcInterfaceCommand` | 점 → `PlcOperation` → `adapter.execute(op)` → 결과. 배율·변경 감지·readBack 그대로 |
| 원격 어댑터(다중화) | `RemotePlcAdapter.execute` 가 작업을 주인 인스턴스 `/execute` 로 넘긴다 — 프로토콜을 모른다 |
| 전달 정의·감사 | 구독 결과도 같은 `ResultDeliveryService` 로(결정 I4) |
| 화면 | 커넥터 설정·인터페이스 settings 는 스키마(`/protocols`)로 그린다 |

## 2. 라이브러리

- **Eclipse Milo 1.1.8** (`org.eclipse.milo:milo-sdk-client`, 사내 Nexus group 에 있음 — 2026-11-01 확인). 시험은 `milo-sdk-server` 로 서버를 띄워서 한다.
- Milo 는 Netty 4.1 로 빌드됐지만 tc 의 Netty 4.2(Spring Boot 4) 위에서 돈다 — 시험으로 확인한다.

## 3. 결정

| # | 질문 | 결정 |
|---|---|---|
| O1 | 커넥터 주소 | `hostIp` + `ports[0]`(기본 4840) + 설정 `endpointPath`(예 `/milo`, 비우면 없음) → `opc.tcp://host:port/path`. 포트는 하나만 쓴다(세션이 다중화를 한다) |
| O2 | 보안 | **D1: SecurityPolicy None + 익명.** 사용자 이름·비밀번호(비밀번호는 `TC_SECRET_KEY` 로 암호화 저장 — DB 연결과 같은 방식), Basic256Sha256 인증서는 D2. 서버가 None 엔드포인트를 주지 않으면 연결 실패로 알린다 |
| O3 | 점 주소 | **NodeId 문자열 그대로** — `ns=2;s=Line1.Temp`, `ns=2;i=1001`, `i=2258`, `ns=3;g=…`, `ns=3;b=…`. 서버 저장 때 문법만 검사, 노드가 있는지는 실행·찾아보기에서 |
| O4 | 형 | BOOL↔Boolean, INT16↔Int16, UINT16↔UInt16, INT32↔Int32, UINT32↔UInt32, INT64↔Int64, FLOAT32↔Float, FLOAT64↔Double, STRING↔String, BYTES↔ByteString(16진). `count > 1` 이면 배열. STRING·BYTES 의 `length` 는 쓰지 않는다. 바이트·워드 순서·워드 안 비트는 없다(400) |
| O5 | 읽기·쓰기 | 인터페이스 하나 = Read 서비스 한 번(노드 여럿). 쓰기도 Write 한 번. 노드 수가 `maxNodesPerRequest`(기본 500)를 넘으면 나눈다. 읽은 값의 형이 정의와 다르면 숫자끼리는 바꾸고(예 Int32 → INT16 범위 안), 못 바꾸면 그 점은 오류 |
| O6 | 상태 | 노드마다 StatusCode. 첫 나쁜 코드를 `plcStatus`(`protocol: OPC_UA`, `code`: 32비트 코드, `text`: `Bad_NodeIdUnknown` 처럼 이름)로. 하나라도 나쁘면 응답은 업무 실패(`ERR_502_TC_PLC_ERROR`) — MELSEC 종료 코드와 같다. 값은 좋은 노드만 싣는다 |
| O7 | 요청 미리보기 | 프레임 대신 서비스 단위 — `Read 3 노드`, 대상 칸에 NodeId 들. `frameHex` 는 비운다 |
| O8 | 구독(SUBSCRIBE) | 인터페이스 settings `accessMode=SUBSCRIBE`(READ 인터페이스만). 커넥터가 연결되면 그 커넥터의 SUBSCRIBE 인터페이스마다 Subscription 하나(점 = MonitoredItem). 값이 바뀌어 알림이 오면 **그 인터페이스의 최근 값 전체**를 응답으로 만들어 전달 정의(WEBHOOK·DB_TABLE·CALLBACK_URL 정의)로 보내고 감사(RESPONSE)를 남긴다(결정 I4). 요청 `sender` 는 `opcua-subscription`. SUBSCRIBE 인터페이스도 `request-and-reply` 로 부르면 그냥 읽는다 |
| O9 | 구독 맞추기 | `OpcUaSubscriptionService` 가 10초마다 "있어야 할 구독"과 "있는 구독"을 맞춘다(인터페이스를 바꾸면 늦어도 10초 뒤 반영) — 인터페이스가 바뀌면 그 구독만 다시 만든다. **주인 인스턴스의 로컬 어댑터만** 구독한다(원격 어댑터는 하지 않음 — 이어받으면 새 주인이 만든다) |
| O10 | 찾아보기(browse) | `POST /api/v1/plc-connector/browse/byName/{name}` `{nodeId}` → 자식 노드(NodeId·이름·노드 종류·데이터 형 → 점 형 추정·자식 있음). 비우면 Objects 폴더. 게시·소유권과 상관없이 **짧은 세션을 따로 열어** 본다(연결 시험과 같다). 권한은 커넥터 조회(R) |
| O11 | 연결 상태 | Milo 세션 상태를 연결 이벤트(CONNECT·DISCONNECT)와 `AdapterStatus`(채널 수 0·1)로 — 연결 모니터·Redis 상태가 그대로 보인다. 재연결은 Milo 가 한다(지수 백오프) |
| O12 | 연결 확인 | 쉬는 연결 확인(루프백)은 하지 않는다 — Milo 가 세션 keep-alive 를 한다. 설정 `healthCheckIntervalSec` 없음 |

## 4. 설정

**커넥터 settings**

| 키 | 기본 | 내용 |
|---|---|---|
| `endpointPath` | (없음) | 엔드포인트 경로 — `opc.tcp://host:port` 뒤에 붙는다 |
| `requestTimeoutMs` | 5000 | 서비스 호출 시간 초과 |
| `sessionTimeoutMs` | 60000 | 세션 시간 초과(서버가 줄일 수 있다) |
| `maxNodesPerRequest` | 500 | Read·Write 한 번의 최대 노드 수 |
| `publishingIntervalMs` | 1000 | 구독 기본 발행 주기 |
| `samplingIntervalMs` | 500 | 구독 기본 샘플링 주기 |

**인터페이스 settings**

| 키 | 내용 |
|---|---|
| `accessMode` | `READ`(기본) · `SUBSCRIBE` |
| `publishingIntervalMs`·`samplingIntervalMs` | 이 인터페이스 구독만 바꿀 때 |
| `deadband` | 절대값 데드밴드(숫자 점, 정수) — 이보다 작게 바뀌면 알리지 않는다. 비우면 없음 |
| `maxNodesPerRequest` | 덮어쓰기 |

## 5. 단계

| 단계 | 내용 |
|---|---|
| **D1** | 드라이버(설정·점 검증·미리보기)·어댑터(연결·읽기·쓰기·상태 이벤트)·연결 시험·찾아보기 API·화면(커넥터 폼은 스키마, 점 편집기 "노드 찾아보기")·SUBSCRIBE → 전달 정의. 시험: 단위(내장 Milo 서버), Playwright |
| D2 | 사용자 이름·비밀번호, 보안 정책·인증서 |
| D3 | 가상 OPC UA 서버(`vplc-opcua`, [virtual-devices.md](virtual-devices.md))로 e2e — **완료**(`test_plc_opcua.py` 21개) |

## 6. D1 구현 결과 (2026-11-01)

| 것 | 내용 |
|---|---|
| 드라이버 | `plc/protocol/opcua/OpcUaDriver` — 설정·점 검증(NodeId 문법, 순서 재정의 거절)·값 변환(O4, 정수 범위 검사)·미리보기(`Read(Value)` N 노드)·`run(client, op)`(Read·Write, 노드 상태 → plcStatus). `OpcUaSessions` 가 세션 설정(None 엔드포인트 고르기, 시간 초과) |
| 어댑터 | `plc/connector/opcua/OpcUaAdapter` — Milo 클라이언트 하나, 처음 연결 실패는 게시 API 면 503·백그라운드면 다시 시도(최대 60초 간격), 세션 활성·비활성 → CONNECT·DISCONNECT 이벤트, `AdapterStatus` 채널 0·1. 커넥터 저장 때 프로토콜이 `OPC_UA` 면 어댑터 클래스를 이것으로 |
| 연결 시험·찾아보기 | `OpcUaTools` — 짧은 세션. 연결 시험은 서버 상태 노드(i=2259) 읽기, 찾아보기는 `POST /api/v1/plc-connector/browse/byName/{name}` `{nodeId}`(조회 권한) → 자식 노드·데이터 형 → 점 형 추정. OPC UA 가 아니면 400 |
| 구독 | `OpcUaSubscriptionService` — SUBSCRIBE READ 인터페이스마다 Subscription, 알림 → 최근 값 전체(배율 적용) → 전달 정의 + 감사 RESPONSE(`sender=opcua-subscription`). SUBSCRIBE 를 WRITE 에 주면 400 |
| 화면 | 커넥터 폼·인터페이스 settings 는 스키마로 그대로, 점 편집기는 영역 검사를 하지 않음(OPC UA 는 영역 없음), 인터페이스 폼 점 탭에 **노드 찾아보기**(폴더를 눌러 들어가고 변수를 골라 점으로 — 이름·NodeId·형 채움) |
| 시험 | 단위 106개(OPC UA 5개 — 내장 Milo 서버로 읽기·쓰기·없는 노드 상태·범위 밖 쓰기·미리보기·연결 시험·찾아보기·어댑터 실행·구독 → 배율 적용 전달·감사). 로컬 tc 에서 프로토콜 목록·커넥터 등록(어댑터 클래스)·서버 없을 때 연결 시험 오류·찾아보기 503·MELSEC 찾아보기 400 확인 |
| 의존성 | `org.eclipse.milo:milo-sdk-client:1.1.8`(시험 `milo-sdk-server`). Netty 4.2.15 위에서 돈다(시험 통과) |

**D3(2026-11-01)** — 가상 OPC UA 서버(`vplc-opcua`)를 상대로 e2e `test_plc_opcua.py` 21개 통과(커넥터·연결 시험·게시·찾아보기·읽기·쓰기·배율·없는 노드·범위 밖·미리보기·**구독 알림 감사**). 이 시험으로 스케줄 스레드에서 인터페이스 목록을 읽을 때 점을 지연 로딩하지 못하던 것(`LazyInitializationException`)을 찾아 `searchInterface()` 를 트랜잭션 안으로 고쳤다 — 단위 시험은 인터페이스 서비스를 흉내 내어 못 잡았다.

## 7. D2 설계 — 로그인·보안 정책·인증서·`nsu=` 주소 (2026-11-01)

실제 현장 OPC UA 서버는 대개 로그인이나 암호화(또는 둘 다)를 요구한다. D1(None·익명)으로는 붙지 못하는 경우가 많아 D2 를 한다.

### 7.1 결정

| # | 질문 | 결정 |
|---|---|---|
| O13 | 로그인 | 커넥터 설정 `authMode` = `ANONYMOUS`(기본) · `USERNAME`, `username`. **비밀번호는 설정 JSON 에 두지 않는다** — 커넥터의 쓰기 전용 칸 `password` 로 받아 `SecretCipher`(`TC_SECRET_KEY`, AES-GCM)로 암호화해 `PLC_CONNECTOR.PASSWORD_ENC` 에 둔다(migration 014). 조회 응답에는 `passwordSet` 만. 수정 때 비우면 저장된 값을 그대로 둔다(DB 연결 비밀번호와 같은 방식). `TC_SECRET_KEY` 가 없으면 비밀번호를 저장할 수 없다(400) |
| O14 | 보안 정책 | 설정 `securityPolicy` = `NONE`(기본) · `BASIC256SHA256` · `AES128_SHA256_RSAOAEP` · `AES256_SHA256_RSAPSS`, `securityMode` = `NONE` · `SIGN` · `SIGN_AND_ENCRYPT`. 정책이 NONE 이면 모드도 NONE, 정책이 있는데 모드가 NONE 이면 `SIGN_AND_ENCRYPT`. 서버의 엔드포인트 목록에서 정책·모드가 같은 것을 고른다 — 없으면 연결 실패(서버가 주는 조합을 오류 문구에 보인다) |
| O15 | tc 클라이언트 인증서 | tc 응용 인스턴스 인증서 하나(RSA 2048·SHA256, applicationUri `urn:wow:tc:opcua-client`, 이 PC 이름·IP). 처음 필요할 때 만들어 `tc.opcua.pki-dir`(기본 `pki/opcua` — 작업 폴더 기준, 로컬은 `run/tc/pki/opcua`)에 PKCS#12(`client.pfx`)로 둔다. 키 저장소 비밀번호는 `TC_SECRET_KEY` 에서 끌어낸다(HMAC) — 키가 없으면 보안 정책을 쓸 수 없다. 서버 관리자가 tc 를 믿게 하려고 **공개 인증서를 내려받는 API**(`GET /api/v1/plc-connector/opcua/client-certificate`, DER)를 둔다. 다중화: 인스턴스마다 다른 인증서가 생기므로 `client.pfx` 를 모든 인스턴스에 같이 둔다(같은 `TC_SECRET_KEY` 면 그대로 열린다) — operations.md |
| O16 | 서버 인증서 믿기 | 설정 `serverTrust` = `TRUST_ON_FIRST_USE`(기본) · `TRUST_ALL`(시험용 — 화면에 경고). 처음 연결 때 받은 서버 인증서를 커넥터별로 `pki/opcua/trusted/<커넥터>.der` 에 남기고, 다음부터 다른 인증서면 거절한다(서버가 바뀌었거나 가로채기). 서버 인증서를 바꾼 게 맞으면 관리자가 **"서버 인증서 잊기"**(`POST /api/v1/plc-connector/opcua/forget-server-certificate/byName/{name}`, 수정 권한)로 지우고 다시 믿는다. 연결 시험·찾아보기도 같은 규칙. 보안 정책이 NONE 이어도 사용자 이름 토큰을 암호화하려면 서버 인증서를 쓰므로 같은 규칙을 탄다 |
| O17 | `nsu=` 주소 | 점 주소에 `nsu=<네임스페이스 URI>;s=…`(i=·g=·b= 도)를 받는다. 실행할 때 세션의 네임스페이스 표로 번호를 찾는다(세션마다 한 번 읽어 둔다). 네임스페이스 번호는 서버가 다시 뜨거나 설정이 바뀌면 달라질 수 있어 **찾아보기는 이제 `nsu=` 형태로 돌려준다**(ns=0 표준 노드는 `i=…` 그대로). `ns=2;…` 형태도 계속 받는다 |

### 7.2 가상 서버(vplc-opcua) — 시험 상대

| 것 | 내용 |
|---|---|
| 엔드포인트 | None(익명·사용자 이름) + Basic256Sha256 Sign·SignAndEncrypt(익명·사용자 이름). 같은 주소 `opc.tcp://127.0.0.1:4840/vplc` |
| 서버 인증서 | 기동 때 자체 서명(메모리) — 다시 뜨면 바뀐다(TOFU 거절 시험에 쓴다) |
| 클라이언트 인증서 | 시험 장비라 기본은 모두 믿는다(`vplc-opcua.security.trust-all-clients`) |
| 사용자 | `vplc-opcua.users`(기본 `tc-e2e` 하나, 비밀번호는 `VPLC_OPCUA_PASSWORD` — 기본값은 시험용, docs/virtual-devices.md). `require-login: true` 면 익명 거절 |
| 두 번째 네임스페이스 | 기본 네임스페이스보다 먼저 `urn:wow:tc:vplc-opcua:extra` 를 등록해 기본 네임스페이스 번호가 **3** 이 되게 한다 — `ns=2` 를 가정한 코드를 잡는다 |

### 7.3 화면

- 커넥터 폼(OPC UA): 보안 정책·모드·로그인 방식·사용자 이름·서버 인증서 믿기는 설정 스키마로 그대로, **비밀번호 칸**(쓰기 전용 — "저장됨" 표시, 비우면 그대로), "tc 클라이언트 인증서 내려받기", "서버 인증서 잊기".
- `TRUST_ALL` 이면 경고 문구.

### 7.4 시험

- tc 단위: 내장 Milo 서버에 Basic256Sha256·사용자 이름 — 맞는·틀린 비밀번호, TOFU(처음 믿고, 서버 인증서가 바뀌면 거절, 잊으면 다시), `nsu=` 주소 읽기·찾아보기.
- e2e `test_plc_opcua.py`: vplc-opcua 에 보안 커넥터(Basic256Sha256 + 사용자 이름), 비밀번호가 조회에 안 나옴, `nsu=` 점, 틀린 비밀번호 연결 시험 실패, 클라이언트 인증서 내려받기.

### 7.5 D2 결과 (2026-11-01)

| 것 | 내용 |
|---|---|
| 세션 | `OpcUaSessions.create(url, settings, 커넥터, 비밀번호)` — 정책·모드가 같은 엔드포인트(호스트·포트는 커넥터 값으로), 없으면 서버가 주는 조합을 오류에. 보안 정책이면 클라이언트 인증서, 보안이나 로그인이면 서버 인증서 검사기, 로그인이면 `UsernameProvider` |
| 인증서 | `OpcUaPki` — `client.pfx`(키 저장소 비밀번호는 `TC_SECRET_KEY` HMAC)·`client.der`, 커넥터별 `trusted/<커넥터>.der`(TOFU). API: `GET /plc-connector/opcua/client-certificate`(DER), `GET …/opcua/server-certificate/byName/{name}`(지문), `POST …/opcua/forget-server-certificate/byName/{name}` |
| 비밀번호 | `PlcConnectorInfo.password`(쓰기 전용)·`passwordSet`, `PLC_CONNECTOR.PASSWORD_ENC`(migration 014). 어댑터는 속성 `passwordEnc` 를 받아 연결할 때만 푼다 |
| `nsu=` | 점 검증은 문법만, 실행·구독·찾아보기 시작 노드는 세션의 네임스페이스 표로. 찾아보기는 `nsu=` 로 돌려준다(ns=0 은 `i=`) |
| 가상 서버 | vplc-opcua — None·Basic256Sha256(Sign·SignAndEncrypt)·익명·사용자 이름(`tc-e2e`), 자체 서명 서버 인증서, 앞 네임스페이스 하나 더(기본 번호 3) |
| 화면 | 커넥터 폼(OPC UA) — 보안 설정은 스키마, 비밀번호 칸(저장됨 표시), TRUST_ALL 경고, 클라이언트 인증서 내려받기, 믿은 서버 인증서 지문·잊기 |
| 시험 | tc 단위 `OpcUaSecurityTest` 4개(암호화·로그인·nsu=·틀린 비밀번호·없는 엔드포인트·TOFU 고정·거절·잊기·TRUST_ALL), vplc-opcua `VirtualOpcUaSecurityTest` 2개, e2e `test_plc_opcua.py` 30개(D2 9개) |
