# wow-tc 시스템 개요 — tc와 vplc

- 작성일: 2026-10-30
- 출처: 사용자 설명 + 현재 코드(`wow-tc-backend/tc`, `wow-tc-backend/vplc`) 대조 결과
- 표의 "코드" 칸: ✅ 구현됨, ⚠️ 일부만 맞음, ❌ 없음

## 1. tc (Tool Control)

### 1.1 무엇인가

tc는 설비에 붙어 있는 PLC와 통신하면서 PLC 데이터를 READ하거나 WRITE하는 서비스를 제공하는 웹 애플리케이션이다(:8080).

| # | 설명 | 코드 |
|---|---|---|
| 1 | PLC 데이터를 READ/WRITE하는 서비스 | ✅ `ProgrammableControllerAdapter.request()` (동기·비동기) |
| 2 | 미쯔비시 PLC 어댑터만 구현. Modbus, OPC UA 어댑터를 추가할 계획 | ⚠️ 어댑터 인터페이스는 있으나 공통 모델이 MELSEC에 맞춰져 있음 (1.4 참고) |
| 3 | PLC 정보를 관리하고, PLC마다 주고받는 인터페이스(READ 또는 WRITE)를, 인터페이스마다 세부 속성(헤더, 데이터)을 정의 | ✅ `PLC_CONNECTOR` → `INTERFACE` → `HEADER_PROP`, `DATA_PROP` |
| 4 | 타 시스템(예: MES)이 REST API로 설비 데이터를 요청하면 결과를 바로 응답하거나, 특정 DB 테이블에 기록하거나, WebHook에 등록된 상대방 REST API로 전달 | 바로 응답 ✅, WebHook과 pushUrl ✅, DB 테이블 기록 ✅ (2026-10-31, [db-table-delivery.md](db-table-delivery.md)) |
| 5 | 요청이 없어도 스케줄로 일정 주기마다 PLC 데이터를 가져와 WebHook을 호출하거나 DB 테이블에 기록 | WebHook ✅ (`PlcGeneralScheduleJob`), DB 테이블 기록 ✅ (같은 전달 정의 경로, 2026-10-31) |
| 6 | 타 시스템 요청은 `DispatcherController`로 받고, 요청 본문은 모두 같은 JSON 구조 | ✅ (1.3 참고) |

### 1.2 처리 흐름

```
타 시스템(MES 등) ──REST──→ DispatcherController ─┐
Quartz 스케줄(일정 주기) ──────────────────────────┤
                                                   ↓
                          MessageDispatcher → 인터페이스(opcode) 조회 → 헤더·데이터 속성으로 PLC 요청 생성
                                                   ↓
                          PLC 어댑터(현재 MELSEC) ──TCP──→ PLC (또는 vplc)
                                                   ↓
                결과 전달: 바로 응답 / pushUrl / WebHook / DB 테이블(DB_TABLE)
                감사 로그 저장(EXCHANGED_MESSAGE_AUDIT) → SSE 알림
```

### 1.3 공통 요청 본문

`Message`와 `TcMessageRequest`(`com.wow.be.tc.msg`, `com.wow.be.tc.dispatch`)가 정의한다.

```json
{
  "transactionId": "...",
  "messageId": "...",
  "opcode": "인터페이스 이름",
  "timestamp": 0,
  "sender": "...",
  "receiver": "...",
  "data": { },
  "sync": false,
  "pushResult": false,
  "pushUrl": "..."
}
```

| 경로 | 동작 |
|---|---|
| `POST /api/v1/ifsvc/request` | 응답 본문 없음. `pushResult`, `pushUrl`로 결과를 따로 보낼 수 있음 |
| `POST /api/v1/ifsvc/request-and-reply` | 결과를 응답으로 바로 돌려줌. 이때 `pushResult`는 false, `pushUrl`은 null이어야 함 |

### 1.4 설계와 코드가 다른 곳

**결과를 특정 DB 테이블에 기록하는 기능이 없다** — **2026-10-31 해결**: DB_TABLE 전달([db-table-delivery.md](db-table-delivery.md)). 아래는 당시 기록
- 지금 DB에 남는 것은 감사 로그(`EXCHANGED_MESSAGE_AUDIT`)뿐이다. 레거시 코드에도 없었다.
- 구현 전에 정할 것:
  - 대상 DB: tc DB인지, 외부 DB인지
  - 테이블·컬럼과 데이터 속성을 어떻게 연결할지
  - WebHook처럼 "전달 대상"의 한 종류로 등록할지

**공통 모델에 MELSEC 개념이 섞여 있다**
- 추상화된 부분: `ProgrammableControllerAdapter`, `PlcRequest`, `PlcResponse`
- MC 프로토콜 바이트 프레임을 전제로 한 부분:
  - 헤더 속성이 order, length, type을 가진 바이트 필드 목록이다.
  - PLC 커넥터에 `frameFormat`(ASCII/BINARY)이 있다.
  - 커넥터가 여러 포트와 VIP 채널을 둔다.
- 프로토콜별 차이:
  - Modbus TCP는 unit id, function code, 레지스터·코일 주소를 헤더 속성에 담으면 비교적 잘 맞는다.
  - OPC UA는 바이트 프레임이 아니라 노드 ID(태그)로 읽고 쓰고, 구독 방식도 있어서 헤더 바이트 모델이 맞지 않는다.
- 권장 방향: 인터페이스 정의를 두 층으로 나눈다.
  - 공통: opcode, 방향(READ/WRITE), 데이터 항목 이름과 타입
  - 어댑터별 주소 지정: MELSEC 헤더, Modbus 주소, OPC UA 노드 ID

## 2. vplc (Virtual PLC)

### 2.1 무엇인가

vplc는 가상 PLC(virtual PLC)다(:8088). 실제 PLC를 구할 수 없어서, tc를 구현해도 시험할 수 없기 때문에 시험용으로 만들었다.

| # | 설명 | 코드 |
|---|---|---|
| 1 | virtual PLC | ✅ |
| 2 | 실제 PLC 대신 tc를 시험하려고 만듦 | ✅ |
| 3 | 미쯔비시용만 구현. tc와 마찬가지로 Modbus, OPC UA용 가상 PLC로 확장할 계획 | ✅ 현재 MELSEC MC 3E만 |
| 4 | tc와 TCP/IP로 통신 | ✅ (2.2 참고) |

### 2.2 tc와 통신하는 방식

- **tc와 vplc는 실제 PLC와 똑같이 TCP 위의 MELSEC MC 3E 프로토콜로 통신한다.** 두 앱 모두 상대방 REST API를 호출하는 코드가 없다. (처음에는 REST로 통신한다고 설명했으나, 코드 확인 후 TCP/IP로 정정했다 — 2026-10-30)
- vplc의 REST API는 사람이나 테스트 도구가 가상 PLC를 관리하고 메모리 값을 넣고 확인하는 관리용이다.

```
tc :8080 ──TCP, MC 3E 바이너리──→ vplc 가상 PLC (기본 포트 12345~)
   MelsecProgrammableControllerAdapter       MessageDecoder → Memory → MessageResponse

브라우저 / 테스트 도구 ──REST──→ vplc :8088 /api/v1/vplc*
   management   : 가상 PLC 등록, 수정, 게시, 삭제
   control      : start, stop, pause, resume
   manipulation : 메모리 read, write, clear (tc가 읽어 갈 값을 미리 넣어 두는 용도)
SSE ──→ 메모리 값이 바뀌면 화면에 알림
```

tc는 vplc가 가상 장비인지 모른 채 실제 PLC처럼 접속한다. 그래서 tc의 PLC 통신 코드를 그대로 시험할 수 있다.

### 2.3 확장할 때 고려할 점

- **tc 어댑터와 짝 맞추기**: tc에 어댑터를 하나 추가할 때마다 vplc에도 같은 프로토콜의 가상 장비를 하나 둔다. 프로토콜 인코딩·디코딩 시험은 양쪽이 같은 테스트 데이터를 쓴다.
- **메모리 모델 분리**: 지금 메모리는 디바이스마다 워드 배열(D, M, X 등)이라 MELSEC에 맞춰져 있다.
  - Modbus는 코일, 입력, 홀딩 레지스터 네 영역으로 나뉘어 비교적 비슷하다.
  - OPC UA는 노드 트리 구조라 저장 방식을 따로 설계해야 한다.
- **현재 한계**: ASCII 모드와 오류 응답이 동작하지 않는다. 그래서 지금은 tc의 정상 경로(바이너리 읽기·쓰기)만 시험할 수 있다. 자세한 것은 [legacy-tc-backend-analysis.md](legacy-tc-backend-analysis.md) 참고.

## 3. 관련 문서

- [legacy-tc-backend-analysis.md](legacy-tc-backend-analysis.md): 레거시 tc-backend 코드 분석과 품질 이슈
- [dispatch-redesign.md](dispatch-redesign.md): tc dispatch 재설계 (요청 → 인터페이스 → PLC → 결과 전달)
- [framework-core-dispatch-changes.md](framework-core-dispatch-changes.md): 재설계를 위한 framework-core dispatch 수정 명세
