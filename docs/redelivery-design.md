# 결과 재전송 (관리자 수동)

> 할 일 #11 (2026-10-31, 설계·백엔드 구현). 자동 재시도(outbox) 대신, 관리자가 송수신 감사 화면을 보고 이미 나간 결과를 골라 다시 보낸다.
> 성공·실패와 관계없이 다시 보낼 수 있다. 전달 경로는 [dispatch-redesign.md](dispatch-redesign.md) §3.3, DB_TABLE 은 [db-table-delivery.md](db-table-delivery.md).

## 1. 왜 자동 재시도가 아니라 수동 재전송인가

| | 자동 재시도(outbox) | 수동 재전송 |
|---|---|---|
| 받는 쪽 장애 | 복구 전까지 계속 실패하며 쌓인다 | 복구를 확인한 사람이 그때 보낸다 |
| tc 는 성공, 받는 쪽이 잃음(받는 쪽 DB 복구, 버그) | 다룰 수 없다 | 다시 보낸다 |
| `onChangeOnly` 정의에서 전달이 한 번 실패 | 재시도가 성공하기 전까지 받는 쪽은 옛 값 | 다시 보내 바로잡는다 |
| 구현 | outbox 표, 재시도 간격, 인스턴스 간 잠금, 실패 확정 | 버튼을 누를 때 한 번 보낸다 |
| 약점 | — | 사람이 보기 전에는 아무도 보내지 않는다 (§7) |

자동 재시도가 꼭 필요해지면 같은 재전송 경로(§4) 위에 스케줄 Job 으로 더한다.

## 2. 무엇으로 다시 보내나 — 송수신 감사(EXCHANGED_MESSAGE_AUDIT)

새 표를 만들지 않는다. 로컬 tc DB 에서 확인한 지금 기록은 다음과 같다(2026-10-31).

| MSG_TYPE | 언제 | MSG_JSON | 그 밖 |
|---|---|---|---|
| REQUEST | 디스패치 전 | 요청 `data` | 호출자 `PRINCIPAL` |
| RESPONSE | PLC 와 교환을 마침(PLC 오류 종료 코드 포함) | 응답 `data` — 숫자는 숫자로 저장된다 | 실패면 `DESCRIPTION` = `코드: 메시지` |
| ERROR | 디스패치 예외(PLC 연결 불가, 인터페이스 없음 등) | `{"errorCode", "errorMessage"}` | `MSG_ID` 를 새로 만든다 |
| DELIVERY | 대상 하나에 전달한 뒤 | `{}` | `RECEIVER` = `유형:이름:주소·테이블`, `DESCRIPTION` = `OK` 또는 실패 사유 |

- 정상 응답은 RESPONSE 와 DELIVERY 의 `MSG_ID` 가 같다 — 전달 한 줄에서 원본 응답을 정확히 찾는다.
- **빈 곳**
  1. PLC 종료 코드(`resultCode`, `resultDescription`)와 `changed` 는 어디에도 없다. WebHook·CALLBACK_URL 본문에는 들어간다.
  2. 디스패치 예외로 만든 실패 응답은 ERROR 와 `MSG_ID` 가 달라, 전달과 원본을 `TRX_ID` + 인터페이스 이름으로만 잇는다. 호출자가 같은 `transactionId` 를 다시 쓰면 모호하다.
  3. 콜백 주소는 DELIVERY 의 `RECEIVER` 에만 있다. REQUEST 에는 `pushUrl` 이 남지 않는다.
- 화면 조회용 변환(`ExchangedMessageAuditEntity.info()`)이 `MSG_JSON` 을 `Map<String, String>` 으로 읽어 숫자가 문자열이 되고, 중첩 객체는 읽지 못한다. 저장된 값은 멀쩡하다.

### 2.1 보완 — DELIVERY 에 보낸 본문을 남긴다

DELIVERY 의 `MSG_JSON` 을 `{}` 대신 아래로 채운다. 빈 곳 1·2 가 사라지고, 전달 기록 한 줄만으로 같은 것을 다시 보낼 수 있다.

```json
{
  "response": { "transactionId": "...", "messageId": "...", "opcode": "...", "timestamp": 0,
                "success": true, "errorCode": null, "errorMessage": null,
                "resultCode": 0, "resultDescription": "...", "changed": true,
                "data": { "d100": 77, "d101": 1 } },
  "redeliveryOf": null
}
```

- `response` 는 채널이 보낸 `TcMessageResponse` 그대로(Jackson 으로 변환).
- `redeliveryOf` 는 재전송이면 원본 DELIVERY 또는 RESPONSE·ERROR 감사 ID, 처음 전달이면 null.
- 화면 변환은 `Map<String, Object>` 로 바꿔 숫자 타입과 중첩 객체를 그대로 보여 준다.
- 크기: 전달 대상 수만큼 응답 data 가 더 쌓인다. 감사 보존은 §7.
- 이 보완 전의 DELIVERY(`{}`)는 §4.2 처럼 원본 RESPONSE·ERROR 에서 응답을 다시 만든다.

빈 곳 3 은 남긴다 — 처음부터 걸러져 보내지 않은 결과는 콜백 주소로 다시 보낼 수 없다. 필요해지면 REQUEST 감사에 `pushUrl` 을 남긴다.

## 3. 결정

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| R1 | 무엇을 다시 보내나 | 처음 만든 **응답 그대로**. PLC 를 다시 읽지 않는다 | 그때의 값을 받는 것이 목적. 지금 값이 필요하면 PLC 요청을 새로 한다 |
| R2 | 재전송 단위 | (가) **전달 기록**을 골라 같은 대상으로, (나) **응답 기록**을 골라 현재 전달 정의 중 고른 대상으로 | (가) 실패·유실 복구, (나) 걸러졌던 결과나 새로 더한 대상에 과거 결과 보내기 |
| R3 | 전달 조건 | `deliverOn`, `onChangeOnly` 를 **보지 않는다** | 관리자가 골라서 보내는 것 |
| R4 | 대상 설정 | **지금 설정**으로 다시 확인한다 — 허용 주소 목록, WebHook 이 있는지와 지금 주소, DB 연결이 있고 사용 중인지. 테이블 칼럼은 보낼 때 드러난다(맞지 않으면 실패 감사) | 그사이 막힌 주소·끈 연결로 보내지 않는다 |
| R5 | 받는 쪽 구분 | HTTP 는 헤더 `X-TC-Redelivery: true`, `X-TC-Redelivery-Of: {감사 ID}`. 본문과 `transactionId` 는 원래대로 | 받는 쪽이 원하면 거르고, 몰라도 기존 처리 그대로 |
| R6 | DB_TABLE 중복 | 같은 `TRANSACTION_ID` 로 행이 하나 더 들어간다. 받는 쪽이 유니크 키(`TRANSACTION_ID`, `INTERFACE_NAME`)를 걸었으면 중복 키 오류를 **"이미 있음" 성공**으로 기록한다 | 표준 테이블을 바꾸지 않는다 |
| R7 | 권한 | 새 자원 `action.tc.redeliver`(X). 기본은 **ADMIN 만** | 운영자에게도 주려면 OPERATOR 표에 더한다 |
| R8 | 한 번에 보낼 수 | 최대 500건, 대상 × 기록(`tc.delivery.redelivery-max-batch`) | 잘못 고른 대량 재전송을 막는다 |
| R9 | 실행 | 검증 후 **202 로 바로 돌려주고** 전달 스레드 풀에서 보낸다. 결과는 DELIVERY 감사와 SSE 로 본다 | 대상이 느리면 500건이 HTTP 제한 시간을 넘는다 |
| R10 | 순서 | 한 요청 안에서 **대상마다 원래 시각 순으로 하나씩** 보낸다 | 받는 쪽에서 옛 값이 새 값을 덮지 않게 |
| R11 | 중복 클릭 | 같은 원본·같은 대상을 10초(`tc.delivery.redelivery-duplicate-window`) 안에 다시 요청하면 그 건은 건너뛴다. **인스턴스 메모리**로 본다 — 두 요청이 서로 다른 인스턴스로 가면 막지 못한다 | 실수로 두 번 누르는 것만 막는다. 일부러 다시 보내는 것은 막지 않는다. 보내기가 뒤에서 돌아 DELIVERY 감사로는 진행 중인 건을 알 수 없다 |

## 4. 동작

### 4.1 전달 기록으로 (R2 가)

1. 감사 ID 마다 `MSG_TYPE=DELIVERY` 인지 확인한다.
2. 응답: `MSG_JSON.response` 가 있으면 그대로, 없으면(보완 전 기록) §4.2 의 방식으로 원본에서 만든다.
3. 대상: `RECEIVER` 를 `유형:이름:나머지` 로 나눈다(주소에 `:` 가 있으므로 세 조각까지만).
   - CALLBACK_URL — 기록된 주소. 허용 주소 목록을 다시 본다.
   - WEBHOOK — 이름으로 지금 WebHook 을 찾아 **지금 주소**로. 지워졌으면 거절.
   - DB_TABLE — 연결 이름과 테이블. 연결이 있고 사용 중인지 본다(`tc` 도 된다).
4. 보낸 뒤 새 DELIVERY 감사를 남긴다 — `PRINCIPAL=user:{id}`, `MSG_JSON.redeliveryOf=원본 ID`, `DESCRIPTION` 은 처음 전달과 같은 규칙(`OK` 또는 실패 사유). 유니크 키 중복(R6)은 `OK (이미 있음(중복 키))`.

### 4.2 응답 기록으로 (R2 나)

1. 감사 ID 마다 `MSG_TYPE` 이 RESPONSE 또는 ERROR 인지 확인한다.
2. 응답을 만든다.
   - RESPONSE: 헤더 칼럼(`TRX_ID`, `MSG_ID`, `NAME`, `SENDER`, `RECEIVER`, `TIMESTAMP`) + `MSG_JSON` 을 `data` 로. `DESCRIPTION` 이 비었으면 성공, 아니면 첫 `: ` 앞을 `errorCode`, 뒤를 `errorMessage` 로.
   - ERROR: 헤더 칼럼 + `MSG_JSON` 의 `errorCode`, `errorMessage`, `success=false`.
   - `resultCode`, `resultDescription`, `changed` 는 비운다(§2 빈 곳 1).
3. 대상: 요청에 적은 대상(유형·이름·테이블)이 그 인터페이스의 **사용 중인 전달 정의**에 있어야 한다. 정의가 없는 인터페이스는 레거시 대상(응답 인터페이스의 WebHook 목록)에서 고른다. CALLBACK_URL 은 고를 수 없다(주소가 없다).
4. 감사는 §4.1 과 같고 `redeliveryOf` 는 RESPONSE·ERROR 의 감사 ID.

### 4.3 코드 위치

- `ResultDeliveryService.deliverNow` — 조건 없이 이 대상에 이 응답을 지금 스레드에서 보내고 감사를 남긴다. 처음 전달과 재전송이 같은 채널·감사 경로를 쓴다.
- `DeliveryTarget.redeliveryOf` — 재전송 표시. HTTP 채널(WebHook, CALLBACK_URL)이 이것을 보고 헤더를 붙이고, DB_TABLE 채널은 중복 키를 `AlreadyDeliveredException` 으로 알린다.
- `delivery.redelivery` 패키지: `AuditResponses`(감사 기록 → 응답·대상), `DefaultRedeliveryService`(검증, 대상별 순서 실행, 중복 클릭).
- `RedeliveryController`, 권한 `TcPermissionChecker` 의 `action.tc.redeliver`.
- 감사 화면 변환(`ExchangedMessageAuditEntity.info()`)을 Jackson `Map<String, Object>` 로 바꿨다(§2).
- 구현 중 고친 기존 버그: `WebHookManager` 가 전역 컨텍스트가 생기기 전에 만들어져, 캐시에 없는 WebHook 이름을 찾으면 NPE(500)가 났다. 지운 WebHook 을 가리키는 전달 정의가 있으면 처음 전달도 이 NPE 로 실패했다. 컨텍스트를 쓸 때 가져오게 했다.

## 5. API

`/api/v1/ifsvc/redelivery` — 권한 `action.tc.redeliver`(X).

```json
POST /api/v1/ifsvc/redelivery/deliveries
{ "auditIds": [1160, 1161] }

POST /api/v1/ifsvc/redelivery/responses
{ "auditIds": [1159],
  "targets": [ { "deliveryType": "DB_TABLE", "targetName": "mes-db", "tableName": "plc_result" },
               { "deliveryType": "WEBHOOK",  "targetName": "mes-hook" } ] }
```

응답 202:

```json
{ "accepted": 2,
  "rejected": [ { "auditId": 1161, "target": "DB_TABLE:mes-db:plc_result", "reason": "사용 중지된 DB 연결입니다: mes-db" } ] }
```

- 검증에서 걸린 건은 `rejected` 로 바로 돌려주고, 나머지만 보낸다. 한 건도 보내지 못하면 400(같은 본문).
- 사유: 감사 기록 없음, 기록 유형이 다름, 대상을 읽을 수 없음, WebHook 없음, 허용되지 않은 주소, DB 연결 없음·사용 중지, 원본 응답 없음, 그 인터페이스의 전달 대상이 아님, 콜백(응답 기록으로는 불가), 방금 같은 재전송을 요청함, 전달 대기열 포화.
- 건수 초과(R8)는 400.
- 고르기는 기존 감사 검색(`POST /api/v1/ifsvc/audit/search/page`)으로 한다. 화면에 필요한 거르기 조건 — 유형(DELIVERY), 대상(`RECEIVER` 앞부분), 결과(`OK` / 실패), 기간, 인터페이스 — 이 검색 조건에 없으면 더한다.

## 6. 화면 (tc 프론트 재작성 때, mes-dev 패턴)

- **전달 이력**: DELIVERY 만, 대상·결과·기간·인터페이스로 거르기, 여러 건 골라 [재전송]. 대상별 최근 실패 수를 위에 보인다.
- **응답 이력**: RESPONSE·ERROR, 행을 고르고 [다른 대상으로 보내기] → 그 인터페이스의 전달 정의 목록에서 대상 선택.
- 보내기 전 확인 창: 건수, 대상, 지금 주소(WebHook 주소가 바뀌었으면 표시), §7 의 순서 경고.
- 재전송 기록에는 "재전송" 표시와 원본 링크.

## 7. 감수할 점

- **사람이 보기 전에는 아무도 다시 보내지 않는다.** 화면의 대상별 실패 수로 빨리 알아차리게 한다. 꼭 도착해야 하는 결과가 생기면 자동 재시도를 이 위에 더한다.
- **순서 역전.** 옛 결과를 다시 보내면 받는 쪽의 최신 값을 덮을 수 있다. 받는 쪽은 `timestamp`(DB_TABLE 은 `COLLECTED_AT`)로 최신인지 판단해야 한다 — 받는 시스템 담당자에게 알린다.
- **tc 가 전달 중에 죽은 결과는 전달 기록이 없다.** 응답 기록으로(§4.2) 다시 보낼 수 있지만 찾아 주지는 않는다. "응답은 있고 전달 기록이 없는 건"은 `onChangeOnly`·`deliverOn` 으로 걸러진 정상 건과 구별되지 않아(`changed` 를 남기지 않는다) 이번에는 만들지 않는다.
- **감사 보존 기간이 곧 재전송 가능 기간이다.** 감사는 데이터 정리 스케줄 Job(`DataTableFlashJob`, 보존 기간 방식)을 등록했을 때만 지워진다 — 없으면 표가 계속 커진다. 보존 기간은 재전송 필요 기간보다 길게 잡는다.
- 감사 저장 자체가 실패하면(경고 로그만 남는다) 그 결과는 다시 보낼 수 없다.

## 8. 시험

- 단위 `AuditResponsesTest`(7): 감사 저장(Gson)·읽기(Jackson)를 거친 응답 복원(성공·PLC 오류·`null` 메시지·ERROR·보낸 본문·보완 전 `{}`), `RECEIVER` 나누기(주소에 `:`). `DbTableDeliveryChannelTest`(2): 중복 키 판별(1062, 23505, 그 밖의 무결성 오류는 아님).
- e2e `test_redelivery.py`(37):
  - 전달 실패(끈 DB 연결) → 끈 채 재전송 거절 → 켜고 재전송 → 표준 테이블에 행, 새 DELIVERY 에 `redeliveryOf`·요청 사용자.
  - 10초 안 같은 재전송 거절, 성공한 전달 재전송(유니크 키 테이블은 "이미 있음", 행 그대로).
  - 본문 없는 옛 기록(RESPONSE 의 MSG_ID, ERROR 의 거래 ID 로 원본 찾기).
  - 응답 기록 → WebHook(`deliverOn: FAILURE` 인데 성공 응답도 보냄), 재전송 헤더, 원래 data. 정의에 없는 대상·콜백 거절. ERROR 기록 → DB_TABLE.
  - 잘못된 요청(유형 다름·없는 ID·빈 목록·501건·대상 없음·토큰 없음), 지워진 WebHook 거절.
- `test_users.py`: 운영자 재전송 403.

## 9. 운영 반영

- 표·칼럼 변경 없음(migration 없음). 재기동은 새 버전 배포 때 함께.
- 배포 뒤 처음 전달부터 DELIVERY 에 본문이 남는다. 그 전 기록은 원본 응답으로 다시 만든다(종료 코드 없음).
- 받는 시스템 담당자에게 알린다: 재전송 헤더, DB_TABLE 중복 행과 유니크 키, 순서 역전.
