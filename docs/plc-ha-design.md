# PLC 연결 다중화(Active-Active) 설계

> 상태: 설계 확정안 (2026-10-30). 1~3단계 완료(3단계는 `tc.plc.ownership.enabled` 로 켠다). 결정 H1~H5·H7~H10 확정(2026-10-31, §9.1·§9.2 검토안대로), H6 은 현장 확인 대기.
> 선행 문서: [plc-connection-design.md](plc-connection-design.md) §6(방향), §5(어댑터 결함 수정 — 이 설계의 기반)
> 대상: `wow-tc-backend/tc`

## 1. 목표와 범위

- **목표**
  - tc를 두 대 이상으로 운영해 **한 대가 죽거나 순차 재기동해도 PLC 요청이 몇 초 안에 이어지게** 한다.
  - "tc를 재기동하면 MES 등이 멈출 수 있다"는 운영 제약을 근본적으로 푼다.
- **원칙**
  - API는 Active-Active다. 어느 인스턴스로 요청이 와도 처리한다.
  - **PLC 연결은 PLC마다 소유 인스턴스가 정확히 하나**다. 같은 PLC에 두 인스턴스가 동시에 연결하지 않는다.
- **범위 밖**
  - DB와 Redis 자체의 이중화(인프라 영역)
  - 로드밸런서 제품 선택

## 2. 전체 구조

```mermaid
flowchart TB
    MES["MES 등"] --> LB["로드밸런서<br/>(헬스 체크)"]
    LB --> A & B
    subgraph A["tc 인스턴스 A"]
        A1["API·디스패처·감사·결과 전달"]
        AO["PlcOwnershipManager"]
        AL["로컬 어댑터: plc-1"]
        AR["원격 어댑터: plc-2 → B"]
    end
    subgraph B["tc 인스턴스 B"]
        B1["API·디스패처·감사·결과 전달"]
        BO["PlcOwnershipManager"]
        BL["로컬 어댑터: plc-2"]
        BR["원격 어댑터: plc-1 → A"]
        BI["/internal/plc/{name}/exchange"]
    end
    A1 --> AL & AR
    B1 --> BL & BR
    AR -- "내부 HTTP (프레임 바이트)" --> BI --> BL
    AL --> P1["PLC 1"]
    BL --> P2["PLC 2"]
    R[("Redis<br/>임대·인스턴스 목록·연결 상태")]
    AO & BO <--> R
    DB[("DB<br/>커넥터 정의·운영자 의도")]
    AO & BO --> DB
```

## 3. 핵심 결정

### 3.1 무엇을 넘기는가: PLC 교환만 넘긴다

- 요청을 받은 인스턴스가 디스패치, 감사, 결과 전달, 변경 감지를 **그대로 한다.** 소유 인스턴스로 넘기는 것은 **PLC 프레임 교환(요청 바이트 → 응답 바이트) 하나뿐**이다.
- 구현은 `ProgrammableControllerAdapter`를 하나 더 두는 것이다. `RemotePlcAdapter`가 `request(req, res)`에서 `req.convertRequest()`로 만든 바이트를 소유 인스턴스로 보내고, 받은 응답 바이트를 `res.splitHeaderAndReplyData()`에 넣는다.
  - 그러면 `PlcInterfaceCommand`, `PlcAdapterManager.get(name)`을 쓰는 코드는 바뀌지 않는다.
- **이렇게 정한 이유**
  - 감사의 `PRINCIPAL`, API 키 사용량 제한, 결과 전달이 요청을 받은 곳에서 한 번만 일어난다. 두 번 기록되거나 빠지지 않는다.
  - 넘기는 계약이 단순하다(바이트 → 바이트). 그래서 PLC 종류(MELSEC, 나중의 Modbus·OPC UA)와 무관하다.

### 3.2 소유권: Redis 임대

| 항목 | 값 |
|---|---|
| 키 | `tc:plc-owner:{plcName}` |
| 값 | `{instanceId}` |
| 획득 | `SET key instanceId NX PX 10000` (TTL 10초) |
| 갱신 | 3초마다. Lua로 "값이 내 ID일 때만 PEXPIRE" |
| 반납 | Lua로 "값이 내 ID일 때만 DEL" |
| 인스턴스 목록 | `tc:instance:{instanceId}` = `{내부 주소}`, TTL 10초, 3초마다 갱신 |

- **instanceId**: `{호스트명}-{기동 시각}`이다. 같은 호스트에서 재기동해도 다른 ID가 되므로, 옛 임대와 섞이지 않는다.
- **내부 주소**: `tc.instance.internal-url`(예: `http://10.0.1.11:8087`). 인스턴스마다 설정한다.

### 3.3 누가 무엇을 소유하는가

- `PlcOwnershipManager`가 2초마다 다음을 한다.
  1. DB에서 **운영자 의도가 PUBLISHED**인 커넥터 목록을 읽는다.
  2. 주인이 없는 PLC의 임대를 시도한다.
  3. 임대를 얻으면 그 PLC의 로컬 어댑터를 만들어 연결한다(§5 어댑터 그대로).
  4. 다른 인스턴스가 주인이면 그쪽을 가리키는 원격 어댑터를 둔다.
- **균형 규칙**: 살아 있는 인스턴스 수를 N, 게시된 PLC 수를 P라 할 때, 이미 `ceil(P/N)`개를 가진 인스턴스는 새 임대를 시도하지 않는다. 한 인스턴스가 기동하자마자 전부 가져가는 것을 막는다.
- **재균형은 자동으로 하지 않는다.** 소유권을 옮기면 짧은 끊김이 생기기 때문이다. 필요하면 관리자가 `POST /api/v1/plc-connector/{name}/handover`로 옮긴다(이력에 남김).

### 3.4 상태의 분리

| 상태 | 저장 위치 | 쓰는 쪽 |
|---|---|---|
| 운영자 의도(게시, 해제) | DB `PLC_CONNECTOR.STATUS1` | 관리 API만 |
| 소유자 | Redis `tc:plc-owner:{name}` | 소유권 관리자 |
| 연결 상태(살아 있는 채널 수, 마지막 오류, 마지막 연결 시각) | Redis `tc:plc-state:{name}` (해시, 소유자가 5초마다 갱신) | 소유자 |
| 연결·끊김 이력 | DB `PLC_CONNECTION_HIST`(지금 그대로) | 소유자 |

- **어댑터 이벤트는 더 이상 DB의 `STATUS1`을 바꾸지 않는다.** 한 인스턴스의 실패가 다른 인스턴스로 번지던 문제가 이것으로 없어진다(plc-connection-design.md §6.1).
- 화면의 커넥터 목록은 DB의 의도와 Redis의 소유자·연결 상태를 합쳐 보여 준다.

#### 3.4.1 2단계 구현 (2026-10-31)

| 항목 | 내용 |
|---|---|
| 의도 | DB `STATUS_1`(PUBLISHED·RELEASED·REGISTERED). 게시·해제 API 만 쓴다. `STATUS_2` 는 **마지막 제어(게시·해제 등) 결과**로 남기고 실제 연결 상태로 쓰지 않는다 |
| 기동 | `PlcConnectorLoader` 는 `STATUS_1=PUBLISHED` 만 본다(예전에는 `STATUS_2=RUNNING` 도 요구해, 마지막 제어가 실패로 남은 게시 커넥터를 올리지 않았다) |
| 이벤트 | `PlcConnectionEventDbHandler` 는 이력(`PLC_CONNECTION_HIST`)과 Redis 만 쓴다. 예전의 `START_UPDATE`·`SHUTDOWN_UPDATE` 이력과 DB 상태 갱신은 없앴다 |
| Redis | 해시 `tc:plc-state:{이름}` — `instance`, `operStatus`, `activeChannels`, `lastEvent`·`lastEventAt`, `lastConnectedAt`, `lastError`·`lastErrorAt`, `updatedAt`. 만료 15초(`tc.plc.state.ttl-ms`), 올라와 있는 어댑터를 5초마다 다시 씀(`tc.plc.state.refresh-interval-ms`). 해제하면 지운다. 정상 종료(이벤트 없음)·사망이면 만료로 사라진다 |
| 마지막 오류 | 연결 실패는 재시도마다 이벤트(DB 이력)를 내지 않고 어댑터가 값만 들고 있다가(`AdapterStatus`) 5초 갱신 때 남긴다. 끊김은 이벤트로 바로 |
| 인스턴스 ID | `InstanceIdentity` — `호스트명-기동시각(ms)`. 3단계 임대 값으로 그대로 쓴다 |
| 조회 API | 커넥터 조회(`find/{id}`, `find/byName`, `search/all`, `search`)에 `connection`(없으면 null) — `status1` 은 의도, `connection` 은 실제. 화면 갱신용 `GET /api/v1/plc-connector/connection-states` |
| Redis 장애 | 쓰지 못하고 넘어간다(PLC 처리 계속), 조회의 `connection` 은 null. 경고는 실패가 시작·회복될 때만 |

## 4. 인스턴스 간 전달

### 4.1 엔드포인트

```
POST /internal/plc/{plcName}/exchange
헤더: X-TC-Instance: {보낸 instanceId}
      X-TC-Signature: HMAC-SHA256(TC_INTERNAL_SECRET, plcName + "\n" + timestamp + "\n" + sha256(body))
      X-TC-Timestamp: {epoch ms}
본문: { "frame": "<base64>", "timeoutMs": 20000, "reqId": "..." }
응답: 200 { "frame": "<base64>" } | 409 NOT_OWNER | 503 PLC_UNAVAILABLE | 504 PLC_TIMEOUT
```

- **별도 보안 체인을 둔다.** `/internal/**`는 사용자 JWT·API 키를 받지 않고 서명만 확인한다. 타임스탬프 ±30초 밖이면 거부해 재전송을 막는다.
- `TC_INTERNAL_SECRET`은 모든 인스턴스가 같은 값을 쓴다. 바꾸려면 모두 재기동해야 하므로, 순차 재기동 중에 끊기지 않도록 **이전 값을 함께 허용하는 기간**을 둔다(`TC_INTERNAL_SECRET_PREVIOUS`).
- **로드밸런서 밖에 둔다.** `/internal/**`를 외부에 노출하지 않도록 LB에서 막고, 가능하면 별도 관리 포트를 쓴다.
- **시간 초과**: 보내는 쪽은 `timeoutMs + 2초`를 기다린다. 받는 쪽 PLC 시간 초과보다 길어야 504가 정확히 전달된다.

### 4.2 받는 쪽이 주인이 아닐 때

- 임대가 막 옮겨진 순간에는 원격 어댑터가 옛 주인에게 보낼 수 있다. 그러면 옛 주인이 **409 NOT_OWNER**를 돌려준다.
- 원격 어댑터는 Redis에서 주인을 다시 읽고 **한 번만** 다시 보낸다. 그래도 실패하면 503이다.
- **WRITE는 다시 보내지 않는다.** 409는 PLC로 보내기 전에 거절한 경우라 중복 쓰기 위험이 없다. 다만 504·연결 오류는 PLC에 이미 갔을 수 있으므로 재시도하지 않는다.

## 5. 장애와 재기동

### 5.1 인스턴스가 갑자기 죽음

```mermaid
sequenceDiagram
    participant A as A (plc-1 주인)
    participant R as Redis
    participant B as B
    participant P as PLC 1
    Note over A: 프로세스 사망 (FIN 없음 가능)
    B->>A: plc-1 교환 전달 → 연결 실패
    B-->>B: 503 (그 사이 요청)
    Note over R: 10초 뒤 임대 만료
    B->>R: SET NX 성공 → B 가 주인
    B->>P: 연결 시도
    alt PLC 가 A 의 죽은 연결을 아직 잡고 있음
        P-->>B: 거부(슬롯 사용 중)
        Note over B: 재연결 백오프 (§5.2 어댑터)
    end
    P-->>B: 연결됨
    Note over B: plc-1 요청 처리 재개
```

- **끊기는 시간** = 임대 TTL(10초) + 소유권 검사 주기(2초) + PLC가 죽은 연결을 정리하는 시간.
- **마지막 항목이 가장 클 수 있다.** A가 FIN 없이 죽으면(전원, 케이블) PLC는 그 연결이 살아 있다고 여기고 슬롯을 계속 잡는다. **PLC의 생존 확인(KeepAlive) 설정을 짧게** 둬야 한다. MELSEC 이더넷 모듈의 "생존 확인" 설정이 이에 해당한다. 이것이 PLC 쪽 필수 설정이다(§7).

### 5.2 계획된 순차 재기동

```mermaid
sequenceDiagram
    participant LB
    participant A as A (재기동 대상)
    participant R as Redis
    participant B as B
    participant P as PLC 1
    LB->>A: 헬스 체크 제외(드레인)
    Note over A: 진행 중 요청을 마칠 때까지 대기 (server.shutdown=graceful)
    A->>P: 연결 닫기 (슬롯 즉시 비움)
    A->>R: 임대 반납 (내 ID일 때만 DEL)
    B->>R: 다음 검사(≤2초)에서 SET NX 성공
    B->>P: 연결
    Note over A: 재기동 → 균형 규칙에 따라 남는 PLC를 소유
    LB->>A: 헬스 체크 통과 → 다시 투입
```

- **끊기는 시간**은 보통 2~3초(검사 주기 + 연결)다. 그 사이 요청은 503이고, 호출하는 시스템이 재시도한다.
- **종료 순서**가 중요하다. ① 새 요청 받지 않기 → ② 진행 중 요청 마치기 → ③ PLC 연결 닫기 → ④ 임대 반납. 지금의 `ForceShutdownHook`(7초 뒤 강제 종료)은 이 순서가 끝날 시간을 줘야 한다. `SmartLifecycle`의 phase로 ③④를 웹 서버 종료 뒤에 둔다.

### 5.3 네트워크 분할과 Redis 장애

| 상황 | 동작 | 이유 |
|---|---|---|
| 주인이 Redis에 닿지 않음(갱신 실패), 다른 인스턴스는 닿음 | 갱신 실패가 **TTL의 절반(5초)** 이어지면 주인이 스스로 PLC 연결을 닫는다(자진 강등) | 다른 인스턴스가 임대를 가져가기 전에 내려놔야 같은 PLC에 두 곳이 연결하지 않는다 |
| Redis 전체 장애(아무도 닿지 않음) | **지금 소유를 유지**한다(fail-static). 새 임대는 아무도 얻지 못한다 | 모두 강등하면 Redis 하나 때문에 PLC 요청 전체가 멈춘다 |
| 위 둘을 구별하는 방법 | 갱신 실패가 이어질 때 **다른 인스턴스의 `tc:instance:*` 갱신이 보이는지**로 판단한다. 아무것도 읽지 못하면 Redis 장애로 본다 | |

- 이 판단은 완벽하지 않다. Redis는 응답하는데 한 인스턴스만 느린 경우 등이 있다. 그래서 **같은 PLC에 두 연결이 생기는 순간이 아주 짧게 있을 수 있다**는 전제로 다음을 둔다.
  - PLC 슬롯이 포트당 1개라면 두 번째 연결은 PLC가 거부하므로 해가 없다.
  - 포트가 여러 개라면 두 인스턴스가 함께 쓸 수 있다. WRITE의 순서가 섞일 수 있는 시간은 길어야 강등 시간(5초)이다.

**구현 때 바로잡음 (3단계, 2026-10-31)** — 위 표의 첫 두 줄은 주인 인스턴스 안에서 가릴 수 없다. 주인만 Redis 에서 끊기면 다른 인스턴스의 `tc:instance:*` 도 읽지 못하므로, 주인이 보기에는 Redis 전체 장애와 똑같다. 그래서 구현은 **Redis 에 닿지 않으면 늘 가진 연결을 유지(fail-static)** 하고 자진 강등은 하지 않는다.

| 상황 | 실제 동작 |
|---|---|
| 주인만 끊김 | 10초 뒤 다른 인스턴스가 임대를 잡고 연결을 시도한다. PLC 가 포트당 연결 하나만 받으면 거부되어 재시도(짧은 고정 간격, H1a)를 이어 가고, 그 사이 그 PLC 요청은 원래 주인으로 온 것만 처리된다. 주인이 Redis 에 다시 닿으면 남의 임대를 보고 강등(연결 닫음) → 새 주인이 연결 |
| Redis 전체 장애 | 모두 가진 연결을 유지한다. 새 임대는 아무도 못 잡는다 |
| 키만 사라짐(만료·FLUSHDB) | 주인이 갱신 때 다시 잡는다(H1b) — 연결은 끊지 않는다 |

- 운영 전제가 하나 늘었다: **PLC 포트마다 연결을 하나만 받게** 설정하는 것이 두 연결을 막는 마지막 장치다(§7). 여러 연결을 받는 포트라면 Redis 에서 끊긴 주인과 새 주인이 함께 쓸 수 있다.

## 6. 바뀌는 코드

| 구분 | 내용 |
|---|---|
| 새로 만듦 | `PlcOwnershipManager`(임대 획득·갱신·반납, 균형 규칙, 자진 강등), `RemotePlcAdapter`, `InternalPlcExchangeController` + 내부 보안 체인(HMAC), `InstanceRegistry` |
| 바뀜 | `PlcConnectorLoader`(기동 때 전부 올리지 않고 소유권 관리자에 맡김), `PlcAdapterManager`(로컬·원격 어댑터를 같은 이름으로 담고 교체), `PlcConnectionEventDbHandler`(DB STATUS1을 바꾸지 않고 Redis 연결 상태만), 게시·해제 API(의도만 DB에 쓰고 소유권 관리자가 따름), 종료 순서(`SmartLifecycle`) |
| 설정 | `tc.instance.internal-url`, `TC_INTERNAL_SECRET`(+`_PREVIOUS`), `tc.plc.ownership.lease-ttl`(10s), `renew-interval`(3s), `scan-interval`(2s), `server.shutdown=graceful` |
| 화면 | 커넥터 목록에 소유 인스턴스·살아 있는 채널 수·마지막 오류. "넘기기(handover)" 버튼 |
| 단일 인스턴스 | 인스턴스가 하나면 모든 PLC를 자기가 소유하므로 지금과 똑같이 동작한다. Redis에 닿지 않으면 fail-static(위)이라 Redis가 늦게 떠도 PLC 요청은 계속된다 |

**로컬 VIP**
- 커넥터의 `connectionVips`는 호스트마다 다르다. 그래서 커넥터 정의에서 인스턴스별 값을 찾게 한다.
- 방법 A: `connectionVips`를 `{instanceHost: [vip...]}` 형태로 넓힌다.
- 방법 B: 인스턴스 설정 `tc.instance.local-vips`에 둔다.
- 권장은 **B**다. 호스트 주소는 인프라 설정이지 커넥터 정의가 아니기 때문이다. 커넥터 정의의 VIP는 단일 인스턴스용 기본값으로 남긴다.

## 7. 운영 전제(PLC·인프라)

- **PLC 생존 확인을 짧게** 설정한다(예: 10~30초). 그러지 않으면 갑자기 죽은 인스턴스의 연결이 슬롯을 오래 잡아, 장애 조치가 그만큼 늦어진다.
- **PLC 포트마다 연결을 하나만 받게** 설정한다 — Redis 에서 끊긴 옛 주인과 새 주인이 같은 PLC 에 함께 붙는 것을 PLC 가 막는다(§5.3 바로잡음).
- **PLC 포트 수 ≥ 한 인스턴스가 쓰는 채널 수.** 소유자 하나만 연결하므로 인스턴스 수와는 무관하다. 이것이 "포트를 나눠 갖는" 방식과의 차이다.
- **LB 헬스 체크용 엔드포인트**가 필요하다(지금은 actuator가 없음). `GET /api/health`(인증 없음, DB·Redis 확인)를 둔다.
- **Redis**: 임대와 상태 키는 `tc:` 접두어를 쓴다. 로컬 Redis를 MES와 함께 쓰고 있으므로, 운영에서는 DB 번호를 나누거나 별도 인스턴스를 권장한다.
- **호출하는 시스템의 재시도**: 장애 조치 동안(수 초) 503이 나므로, READ는 재시도하게 하고 WRITE는 결과 확인 뒤 재시도하게 연동 가이드에 적는다.

## 8. 시험 계획

| 시나리오 | 확인 |
|---|---|
| 인스턴스 2대 + vplc(포트 2개) + 가짜 PLC | 소유 분배(균형 규칙), 비소유 인스턴스로 온 요청의 전달, 감사 1회 기록 |
| 주인 강제 종료 | 12초 안팎에 다른 인스턴스가 이어받아 200. 그 사이 503 |
| 순차 재기동(정상 종료) | 끊김 3초 이하. 옛 주인이 임대를 반납하는지 |
| 소유권 이동 중 요청 | 409 NOT_OWNER → 한 번 재시도 → 200 |
| 주인의 Redis 차단(방화벽) | 5초 안에 자진 강등, 다른 인스턴스가 이어받음 |
| Redis 전체 중지 | 기존 소유 유지(요청 계속 200), 새 게시는 소유자 없음 |
| 서명 위조·시각 초과 내부 호출 | 401 |
| 단일 인스턴스 | 지금과 같은 동작(회귀) |

## 9. 결정

**2026-10-31 확정** — H1~H5 는 아래 제안에 §9.1 의 "보탤 것"을 더해, H7~H10 은 §9.1·§9.2 제안대로. **H6 은 현장 PLC 담당 확인 대기**(구현을 막지는 않는다 — 장애 조치 시간에만 영향).

| # | 질문 | 결정 |
|---|---|---|
| H1 | 임대 TTL·갱신·검사 주기 | 10초·3초·2초 |
| H2 | 인스턴스별 로컬 VIP 위치 | 인스턴스 설정(`tc.instance.local-vips`) |
| H3 | 내부 호출 포트 | 별도 관리 포트(예: 8187), 불가하면 같은 포트 + LB 차단 |
| H4 | 자동 재균형 | 하지 않음. 수동 handover만 |
| H5 | Redis를 MES와 공유할지 | 운영은 분리(별도 인스턴스나 DB 번호) |
| H6 | 실제 PLC의 생존 확인 설정값 | ⏸ 현장 PLC 담당과 확인(10~30초 권장) |

### 9.1 검토 (2026-10-31, 그대로 확정)

1단계 이후 코드를 다시 보고 정리했다. 제안을 그대로 두는 것과 보탤 것, 그리고 표에 없던 결정이다.

| # | 검토 의견 | 보탤 것 |
|---|---|---|
| H1 | **10초·3초·2초 유지.** 갱신 기회가 만료 전 3번이고, 자진 강등(5초)이 만료(10초)보다 확실히 앞선다. TTL 을 줄이면 장애 조치는 빨라지지만 GC 멈춤·Redis 지연에 강등이 잦아진다 | (a) 넘겨받은 직후 연결은 **짧은 고정 간격**(예: 2초)으로 시도한다. 지금 재연결은 1.5초씩 늘어 최대 30초(`plc.connection.retry`, `MAX_RECONNECT_INTERVAL_MS`)라, PLC 가 죽은 연결을 오래 잡으면 그만큼 더 늦게 붙는다. (b) 갱신 때 키가 **없으면**(만료·누가 지움) 강등하지 말고 `SET NX` 로 다시 잡는다 — 값이 남의 ID 일 때만 강등 |
| H2 | **B(인스턴스 설정) 유지.** 비우면 커넥터 정의의 VIP 를 쓴다(단일 인스턴스 그대로) | 기동 때 `local-vips` 수가 커넥터 포트 수보다 적으면 경고. 채널 수는 지금도 `min(포트 수, VIP 수)` 다 |
| H3 | **별도 포트(8187) 권장.** Tomcat 커넥터를 하나 더 열고, 필터가 `/internal/**` 은 그 포트로만, 그 포트로는 `/internal/**` 만 받게 한다. LB 설정 실수로 열려도 서명이 막지만, 포트로 한 번 더 막는 것이 싸다 | 방화벽은 tc 인스턴스끼리만 8187 허용 |
| H4 | **자동 재균형 안 함 유지.** 다만 균형 규칙은 새 임대에만 걸리므로, 두 대를 차례로 재기동하면 **마지막에 뜬 인스턴스는 PLC 를 하나도 갖지 않는다.** PLC 교환은 바이트 몇 개라 한쪽에 몰려도 처리에는 문제가 없고, 영향은 "그 인스턴스가 죽으면 모든 PLC 가 한꺼번에 넘어간다"는 장애 범위뿐이다 | 쏠림을 정상으로 본다. handover 는 꼭 필요할 때만(운영 문서에 적음). 화면에 인스턴스별 소유 수를 보여 준다 |
| H5 | **운영은 분리(최소 DB 번호).** MES 쪽에서 `FLUSHDB` 를 하면 임대가 한꺼번에 사라진다 — H1(b)를 넣으면 주인이 바로 다시 잡아 끊기지 않지만, 토큰·사용량 키도 함께 사라진다 | Redis 연결 설정에 DB 번호를 둔다(`spring.data.redis.database`) |
| H6 | 현장 확인이 필요하다 — 정할 수 없음. MELSEC 이더넷 모듈의 생존 확인(시작 간격·확인 간격·재전송 횟수)으로 **죽은 연결이 30초 안에 정리**되는지 본다 | 확인 전에는 갑작스러운 장애 때 이어받기가 PLC 정리 시간만큼 늦어진다는 것을 운영 문서에 적는다 |

**표에 없던 결정 — 인스턴스마다 따로 있는 상태**

| # | 무엇 | 두 대일 때 문제 | 제안 |
|---|---|---|---|
| H7 | WebHook 캐시(`WebHookManager`, `HashMap`) | 한 인스턴스에서 WebHook 주소를 바꾸거나 지워도 다른 인스턴스는 재기동 전까지 옛 주소로 보낸다. `HashMap` 이라 동시 접근에도 안전하지 않다 | 짧은 만료(예: 10초)의 캐시로 바꾸거나 매번 DB 에서 읽는다(전달 1건당 조회 1번). DB 연결 풀이 "기록 때마다 저장된 정보와 비교"하는 것과 같은 방식 |
| H8 | SSE(감사·커넥터 이벤트, `SseEmitterManager`) | 화면은 LB 로 한 인스턴스에만 붙는다 — 다른 인스턴스가 처리한 요청의 감사가 실시간으로 보이지 않는다 | Redis pub/sub 로 이벤트를 모든 인스턴스에 뿌리고 각자 자기 SSE 구독자에게 보낸다. 새 프론트 작업 전에 정하면 좋다 |
| H9 | 재전송 중복 클릭(R11) | 인스턴스 메모리라 두 요청이 다른 인스턴스로 가면 못 막는다 | 그대로 둔다(실수 방지용). 로그인 제한·API 사용량·전환 스위치 같은 런타임 옵션은 이미 Redis 라 공유된다 |
| H10 | 스케줄 Job 의 겹침·복구(아래 §9.2) | Quartz 클러스터는 "한 실행 시각 = 한 인스턴스"만 보장한다. 실행이 주기보다 길면 다음 실행이 겹치고, 실행 중 인스턴스가 죽으면 그 실행은 다시 돌지 않는다 | Job 종류별로 정한다 — §9.2 |

- 이미 되어 있는 것: Quartz 는 클러스터 모드(`isClustered: true`)라 스케줄 Job 은 한 인스턴스에서만 돈다. Job 이 주인이 아닌 인스턴스에서 돌면 원격 어댑터로 넘어간다(§3.1 그대로). API 키 캐시·DB 연결 풀은 저장된 값과 비교하거나 만료가 있어 다른 인스턴스의 변경을 따라온다.
- 순서: H7·H10 은 다중화와 별개로 지금도 고칠 만한 결함이라 2단계 앞에 둔다. H8 은 4단계(원격 어댑터)와 함께.

### 9.2 스케줄 Job 중복·복구 (H10)

**지금 보장되는 것** — Quartz 클러스터(JDBC JobStore, `isClustered: true`)

- 트리거를 가져갈 때 `QRTZ_LOCKS` 의 `TRIGGER_ACCESS` 행을 DB 행 잠금으로 잡고 트리거를 ACQUIRED 로 바꾼다 → **한 실행 시각은 한 인스턴스만** 실행한다.
- 인스턴스는 20초(`clusterCheckinInterval`)마다 `QRTZ_SCHEDULER_STATE` 에 체크인한다. 끊기면 다른 인스턴스가 그 인스턴스가 가져갔던 트리거를 되돌려 이어 실행한다. 60초(`misfireThreshold`) 넘게 밀린 실행은 놓친 실행으로 처리한다.
- 전제: 모든 인스턴스가 같은 `instanceName`, `instanceId: AUTO`, 같은 DB, **시계 동기화(NTP)**.

**빈 곳**

| # | 상황 | 지금 동작 | 영향 |
|---|---|---|---|
| a | 실행 시간 > 주기 | Job 클래스에 `@DisallowConcurrentExecution` 이 없어 다음 실행이 다른 인스턴스(또는 같은 인스턴스의 다른 스레드)에서 **겹쳐 돈다** | 같은 PLC 수집이 겹치면 값 순서가 섞이고(`onChangeOnly` 비교 포함) DB_TABLE 에 행이 겹친다. PLC 가 느릴 때(시간 초과 3초 × 재시도) 생기기 쉽다 |
| b | 실행 중 인스턴스 사망 | JobDetail 에 `requestRecovery` 가 없어 **그 실행은 다시 돌지 않는다.** 다음 주기에 정상 실행 | 주기 수집은 한 번 빠질 뿐. 한 번만 도는 WRITE Job 이면 쓰기가 빠질 수 있다 |
| c | 인스턴스마다 돌아야 하는 일 | 클러스터 Job 은 한 인스턴스에서만 돈다 | 자기 어댑터를 점검하는 일은 Quartz Job 으로 만들면 안 된다. `PlcConnectorSpyScheduleJob` 은 지금 빈 껍데기라 해는 없다 — 다중화 때는 소유권 관리자(§3.3)가 맡는다 |

**결정**

1. `PlcGeneralScheduleJob` 에 `@DisallowConcurrentExecution` — 같은 Job(JobKey)은 클러스터 전체에서 한 번에 하나만 돈다. 앞 실행이 끝나지 않았으면 다음 실행은 끝날 때까지 기다리고, 60초를 넘기면 놓친 실행 규칙을 따른다. 정리 Job(`DataTableFlashJob`)도 같이 붙인다.
2. `requestRecovery` 는 **켜지 않는다(기본).** 주기 수집은 다음 실행이 곧 오고, 죽은 실행을 다시 돌리면 이미 PLC 에 간 WRITE 가 한 번 더 갈 수 있다 — WRITE 는 재시도하지 않는다는 §4.2 와 같은 원칙. 한 번만 도는 WRITE Job 이 생기면 그때 Job 별로 정한다.
3. 인스턴스별 점검은 Quartz 가 아니라 각 인스턴스의 스케줄러(소유권 관리자 등)로 한다. `PlcConnectorSpyScheduleJob` 은 빈 껍데기지만 **지우지 않는다** — 운영 DB 에 옛 이름으로 등록돼 있으면 클래스가 없어 그 Job 을 올리지 못한다. 운영 DB 의 `SCHEDULE_JOB` 을 확인한 뒤 함께 정리한다.
4. 운영 문서에 "모든 tc 서버 NTP 동기화"를 적는다.

## 10. 구현 순서

1. **기반**: `/api/health`, `server.shutdown=graceful`, 종료 순서(`SmartLifecycle`). 단일 인스턴스에서도 이득이다. — **완료(2026-10-30)**: `HealthController`, `PlcShutdownLifecycle`, 단계별 상한 25초. 진행 중 요청이 끝난 뒤 PLC 연결이 닫히는 것을 확인했다(e2e `test_shutdown.py`).
   - **1-1 다중화 전 정리**: H7(WebHook 캐시), H10(스케줄 Job 겹침 방지). 단일 인스턴스에도 이득이다. — **완료(2026-10-31)**: `WebHookManager` 를 10초 만료·스레드 안전 캐시로(없는 이름도 기억), `PlcGeneralScheduleJob`·`DataTableFlashJob` 에 `@DisallowConcurrentExecution`. e2e `test_webhook_cache.py`, 단위 `ScheduleJobConcurrencyTest`.
2. **상태 분리**: 어댑터 이벤트가 DB `STATUS1`을 바꾸지 않게 하고, Redis 연결 상태를 남긴다. 화면 표시를 바꾼다. — **완료(2026-10-31)**: §3.4.1. 화면은 새 프론트에서 `connection` 을 쓴다. e2e `test_plc_state.py`.
3. **소유권 관리자**: 단일 인스턴스에서 임대를 잡고 지금처럼 동작하는지 확인한다. — **완료(2026-10-31)**:
   - `PlcOwnershipManager`(임대 획득·갱신·반납, 균형 규칙, 강등, 키가 사라지면 다시 잡기), `InstanceRegistry`(`tc:instance:{id}`), 갱신·검사는 각자 스레드.
   - **`tc.plc.ownership.enabled`(기본 false)** — 끄면 지금과 같다(기동 때 전부 연결, Redis 없이도 PLC 요청). 켜면 PLC 연결이 Redis 임대에 달려, 기동 때 Redis 가 없으면 아무 PLC 에도 연결하지 않는다. 다중화로 운영할 때만 켠다(재기동 필요).
   - 게시 API 는 임대를 잡을 수 있으면 바로 연결(실패를 알림), 다른 인스턴스가 주인이면 의도만 남긴다. 해제 API 는 의도가 커밋된 뒤 임대를 반납한다 — 그 사이 검사가 옛 의도로 다시 연결하지 않게, API 가 다루는 커넥터는 검사가 건너뛴다.
   - 넘겨받은 직후 30초 동안 재연결은 2초 고정 간격(H1a, `takeover-fast-retry`·`takeover-retry-interval`).
   - 종료: 새 임대 멈춤 → 연결 닫기 → 임대 반납·인스턴스 목록에서 빠짐(§5.2 ③④).
   - 다른 인스턴스가 주인인 PLC 로 온 요청은 아직 503 이다(4단계 원격 어댑터).
   - 시험: e2e `test_plc_ownership.py`(17, 켠 상태에서만), 켠 상태·끈 상태 모두 정상 종료 포함 전체 e2e 통과.
4. **원격 어댑터와 내부 전달**(HMAC 체인)
5. **두 인스턴스 시험**(§8), 그 뒤 운영 문서(재기동 절차, PLC 설정)
