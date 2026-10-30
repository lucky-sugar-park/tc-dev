# tc dispatch 재설계

- 작성일: 2026-10-30
- 대상: `wow-tc-backend/tc`의 `com.wow.be.tc.dispatch`, framework-core `com.wow.be.framework.dispatch`
- 결정: framework-core dispatch를 사용자가 직접 고쳐 Nexus에 배포한 뒤(안 A), tc를 그 위에 다시 짠다. framework 쪽 수정 명세는 [framework-core-dispatch-changes.md](framework-core-dispatch-changes.md)에 있다.

## 1. dispatch가 하는 일

`DispatcherController`(또는 스케줄 Job)로 들어온 요청에서 인터페이스(opcode)를 식별한다. 그 인터페이스에 연결된 PLC에 접속해 데이터를 읽거나 쓴다. 결과는 인터페이스에 정의된 방식으로 돌려준다.

| 반환 방식 | 설명 |
|---|---|
| 동기 리턴 | 요청의 HTTP 응답 본문으로 결과를 돌려준다 |
| 비동기 리턴 | 요청에 담긴 return url(callback)을 호출한다 |
| WebHook | 인터페이스에 등록된 상대방 REST API를 호출한다 |
| DB Insert | 지정한 DB 테이블에 결과를 넣는다 |

## 2. 현재 상태

### 2.1 framework-core dispatch

범용 커널이다. opcode에 맞는 커맨드를 찾고, `@Command` 어노테이션이나 yml로 매핑하고, callback을 지원한다. **mes-dev 전체에서 쓰는 곳이 없다**(grep으로 확인). 그런데 tc가 상속해서 쓰기 어려운 구조다.

- `MessageCommandFactory`가 static 싱글턴이고 생성자가 private이다. 그래서 override하거나 교체할 수 없다.
- `@Command` 클래스 탐색이 `java.class.path`를 뒤진다. Spring Boot 실행 jar 안의 `BOOT-INF/lib`는 여기에 나오지 않아서, 배포하면 커맨드를 찾지 못한다.
- 커맨드인지 판별할 때 `getInterfaces()`를 비교한다. 그래서 `GenericMessageCommand`를 상속한 클래스는 등록되지 않는다. 예제인 `DispatchTestCommand` 자신도 등록되지 않는다.
- 커맨드를 리플렉션으로 직접 만들어서 Spring 빈이 아니다(DI 불가).
- `ResponseMessage.payload`가 `Map<String,String>`이라 값의 타입이 사라진다. 필드 이름도 `transactionid`로 소문자 i 오타가 있다.
- 예외를 메시지 없이 다시 던지고, `DispatcherCommandLoader`가 `System.exit(0)`을 호출한다.

tc의 `Command`, `CommandInfo`, `DispatcherConfig`, `DispatcherCommandLoader`, `MessageCommandFactory`는 이 코드를 거의 그대로 복사한 것이다. 그래서 위 문제를 똑같이 가지고 있다.

### 2.2 tc dispatch의 문제

**1. 반환 방식이 여러 곳에서 정해지고, 인터페이스 정의는 쓰이지 않는다.**

| 반환 방식 | 지금 누가 정하나 |
|---|---|
| 동기 리턴 | 엔드포인트(`/request-and-reply`) |
| 비동기 실행 여부 | 요청 본문의 `sync`. `InterfaceInfo.sync`는 어디서도 읽지 않음 |
| return url 호출 | 요청 본문의 `pushResult`, `pushUrl` (PUT) |
| WebHook | `PlcGeneralScheduleJob`에만 있음 (POST). REST 요청 결과는 WebHook으로 보낼 수 없음 |
| DB Insert | 없음 |

그래서 스케줄 결과는 return url로 보낼 수 없고, REST 요청 결과는 WebHook으로 보낼 수 없다.

**2. 비동기 실패가 사라진다.** 호출할 때마다 `Executors.newCachedThreadPool()`로 스레드 풀을 새로 만들고, 실패하면 `log.warn("")`만 남긴다. 호출자도 감사 로그도 실패를 알 수 없다.

**3. 커맨드가 Spring 빈이 아니다.** 의존성을 `SessionContext`에서 직접 꺼내 쓰고, `GenericMessageCommand`는 static 블록에서 빈을 가져온다. 그래서 테스트하기 어렵다.

**4. 응답 해석이 MELSEC에 묶여 있다.** `PlcResponse.extractReplypData`가 리틀엔디안을 고정으로 쓰고, DOUBLE을 float로 읽는다. 빅엔디안인 Modbus나, 값에 타입이 붙어 오는 OPC UA로 확장할 수 없다.

**5. 변경 감지가 깨져 있다.** `GenericMessageCommand.checkIfChange`의 `before=null` 때문에 같은 opcode의 두 번째 호출부터 NPE가 난다. 비교 기준도 opcode 하나뿐이라, 같은 opcode로 다른 주소를 요청해도 구분하지 못한다. 감지 결과를 쓰는 곳도 없다.

**6. 오류 모델이 없다.** 실패하면 예외가 그대로 500 오류가 되고 응답에 `transactionId`도 없다. 또 PLC가 돌려준 결과 코드와 tc의 처리 결과가 한 필드(`resultCode`)에 섞여 있다.

**7. 외부 호출 방식이 제각각이다.** pushUrl은 PUT, WebHook은 POST로 보낸다. `RestTemplate`에 타임아웃이 없고, 호출 대상 URL을 검증하지 않아 SSRF가 가능하다.

## 3. 목표 구조

두 층으로 나눈다.
- **층 A, 범용 Dispatch 커널**: opcode로 커맨드를 찾아 실행한다. tc를 전혀 모른다. framework-core에 둔다.
- **층 B, tc 인터페이스 파이프라인**: 인터페이스 해석, PLC 교환, 결과 전달, 감사 기록을 맡는다. tc에 둔다.

또 **결과를 어떻게 돌려줄지는 요청이 아니라 인터페이스 정의가 정한다.**

```
DispatcherController ─┐                               ┌→ 동기 리턴 (/request-and-reply 응답 본문)
스케줄 Job ────────────┼→ ① 접수·검증 → ② 인터페이스 해석 ┤
(같은 경로로 통합)       │  ③ 커맨드 선택 → ④ PLC 교환       ├→ CALLBACK_URL (요청의 return url, 허용된 경우)
                       │  ⑤ 결과 해석(어댑터별 값 코덱)    ├→ WEBHOOK
                       │                                 └→ DB_TABLE (신규)
                       └→ ⑦ 감사·SSE (요청/응답/실패/전달 결과 모두)       ⑥ 결과 전달
```

### 3.1 층 A: 범용 Dispatch 커널 (framework-core)

자세한 명세는 [framework-core-dispatch-changes.md](framework-core-dispatch-changes.md).

- **커맨드는 Spring 빈**이다. 커맨드 찾기는 `CommandResolver` 체인이 맡는다.
  1. `@Command`가 붙은 빈
  2. yml 매핑
  3. 위에서 못 찾으면 "커맨드 없음" 오류
- **tc의 override 지점**: tc는 `InterfaceCommandResolver` 빈을 우선순위를 높여 추가한다. 이 빈이 `INTERFACE.CMD_CLASS_NAME`으로 커맨드를 찾는다.
- **메시지 기본 타입**: `RequestMessage`와 `ResponseMessage`에 표준 헤더(transactionId, messageId, opcode, timestamp, sender, receiver)와 `Map<String,Object>` 형태의 data를 둔다. tc의 `Message` 필드와 이름이 같으므로 외부 시스템이 보내는 JSON 형식은 바뀌지 않는다.
- **실행 방식**: 동기, 비동기(`CompletableFuture`), callback 세 가지다. 스레드 풀은 Spring `TaskExecutor`를 주입받는다. 실패는 숨기지 않고 호출자에게 전한다.
- **`DispatchInterceptor`**: 디스패치 앞, 뒤, 오류 시점에 끼어드는 지점이다. tc의 감사 기록과 SSE를 여기에 연결한다.

### 3.2 층 B: tc 인터페이스 파이프라인

- **기본 커맨드 `PlcInterfaceCommand`**: 인터페이스별 맞춤 커맨드는 이것을 상속해서 `beforeExchange`, `afterExchange` 같은 전·후처리 지점만 override한다(템플릿 메서드).
- **`PlcExchangeService`**: 어댑터를 찾고, 인터페이스별 타임아웃을 적용하고, 오류를 분류한다.
- **`ValueCodec`(어댑터별)**: 응답 바이트나 값을 응답 인터페이스의 데이터 속성에 맞춰 `Map<String,Object>`로 바꾼다. MELSEC은 리틀엔디안, Modbus는 빅엔디안, OPC UA는 이미 타입이 붙은 값을 받는다.
- **결과 전달 정의 `INTERFACE_DELIVERY`(신규 테이블)**: 인터페이스마다 다음을 담는다.
  - 전달 유형: `CALLBACK_URL`, `WEBHOOK`, `DB_TABLE`
  - 대상 이름
  - "값이 바뀔 때만 전달" 옵션
  
  지금 있는 `webHookNameList`는 이 테이블로 옮긴다.
- **`ResultDeliveryChannel` 전략 빈**: 전달 유형마다 구현체를 하나씩 둔다(Kafka, SSE 전달은 나중에 빈만 추가하면 된다). 전달 대상마다 서로 독립적으로, 전용 스레드 풀에서 실행하고 결과를 기록한다.
- **동기·비동기는 엔드포인트가 정한다.** 요청 본문의 `sync`는 없앤다.
  - `/request-and-reply`: 결과를 응답 본문으로 돌려준다. 인터페이스에 정의된 다른 전달 대상도 함께 실행한다.
  - `/request`: `202 Accepted`와 `{transactionId}`를 바로 돌려주고, 결과는 정의된 전달 대상으로 보낸다.
  - 스케줄 Job: `/request`와 같은 경로로 들어간다. `PlcGeneralScheduleJob`의 WebHook 코드는 지운다.
- **오류 모델**
  - framework `ResponseMessage`의 `success`, `errorCode`, `errorMessage`를 쓴다.
  - tc 오류 코드: `TC_INTERFACE_NOT_FOUND`, `TC_PLC_UNAVAILABLE`, `TC_PLC_TIMEOUT`, `TC_PLC_ERROR`, `TC_INVALID_REQUEST`
  - PLC가 돌려준 결과 코드는 `plcResultCode`로 따로 둔다.
  - 오류 응답에도 항상 `transactionId`를 넣는다.
- **외부 호출**: `RestClient` 하나로 통일하고 타임아웃을 둔다. 호출 대상 URL은 허용 목록으로 검사한다.

### 3.3 DB Insert 채널

- **대상 DB**: 설정에 이름으로 등록한 DataSource만 쓸 수 있다. 접속 정보는 환경 변수로 받는다.
- **연결 정의**: 테이블 이름, "데이터 속성 → 컬럼" 매핑, 공통 컬럼(transactionId, opcode, plcName, 수집 시각)을 정의에 담는다.
- **SQL 인젝션 방지**: 테이블·컬럼 이름은 등록할 때 JDBC 메타데이터로 실제로 있는지 검증한다. 실행은 `NamedParameterJdbcTemplate`으로 하고, 요청에서 온 문자열은 SQL에 절대 붙이지 않는다.

## 4. 진행 순서

| 단계 | 작업 | 담당 |
|---|---|---|
| 0 | framework-core dispatch 수정 후 Nexus 배포 | 사용자 |
| 1 | 변경 감지 NPE 수정 (핵심 경로 버그) | tc |
| 2 | tc 자체 dispatch 복사본 삭제, framework 커널로 전환, `InterfaceCommandResolver`·`PlcInterfaceCommand` 작성 | tc |
| 3 | `TcMessageRequest`/`Response`와 오류 모델 정리 | tc |
| 4 | 전달 채널 추상화: callback과 WebHook을 채널로 옮기고 스케줄 Job을 같은 경로로 통합 | tc |
| 5 | `INTERFACE_DELIVERY` 테이블과 관리 API | tc |
| 6 | DB_TABLE 채널 | tc |
| 7 | 어댑터별 값 코덱 분리 (Modbus 확장 준비) | tc |
| 8 | vplc 통합 테스트: 동기, 비동기, WebHook, DB 경로 | tc |

### 4.1 진행 상황 (2026-10-30)

framework-core 0.8.45(같은 버전으로 재배포, Nexus 15:18)를 받아 1~4단계를 했다. tc와 vplc 컴파일까지 확인했고, **실행 확인(vplc 연동)은 아직 안 했다**.

| 단계 | 상태 | 내용 |
|---|---|---|
| 1 | ✅ | 변경 감지: `DefaultPlcResponseService`가 Redis 리포지터리에 실제로 읽고 쓴다. 레거시는 저장 코드가 주석이라 아무것도 기억하지 않았다. Redis를 쓸 수 없으면 "바뀜"으로 보고 PLC 요청은 계속한다 |
| 2 | ✅ | tc 자체 dispatch 복사본 8개 삭제. `InterfaceCommandResolver`(순서 50), `PlcInterfaceCommand`(`AbstractMessageCommand` 상속, `beforeExchange`/`afterExchange` 훅), `PlcExchangeContext` 추가. 옛 DB 값 `…GenericMessageCommand`는 `LegacyClassNames`가 `PlcInterfaceCommand`로 대신 찾는다 |
| 3 | ✅ | `TcMessageRequest extends RequestMessage`, `TcMessageResponse extends ResponseMessage`. 오류 코드 `ERR_{HTTP상태}_TC_…`. `AuditDispatchInterceptor`가 REQUEST/RESPONSE/ERROR 감사 기록 |
| 4 | ✅ | `ResultDeliveryService` + 채널 2개(`CALLBACK_URL` PUT, `WEBHOOK` POST). 전용 스레드 풀(`deliveryTaskExecutor`), 타임아웃 있는 `RestClient`, 주소 허용 목록(`tc.delivery.allowed-url-prefixes`). 전달 결과도 감사 기록(DELIVERY). 스케줄 Job이 REST와 같은 경로를 탄다 |
| 5 | ✅ | `INTERFACE_DELIVERY` 테이블과 관리 API (`/api/v1/ifsvc/delivery`). 아래 4.2절 |
| 6~8 | 대기 | DB_TABLE 채널, 어댑터별 값 코덱, vplc 통합 테스트 |

**구현하면서 3절과 달라진 점**
- **응답 필드 이름**: PLC 종료 코드는 3절의 `plcResultCode`가 아니라 레거시와 같은 `resultCode`/`resultDescription`으로 두었다. 외부 시스템이 이미 읽고 있을 수 있어서다.
- **HTTP 상태**: PLC와 교환을 마쳤으면 PLC가 오류 종료 코드를 돌려줘도 200이고, 본문의 `success=false`, `errorCode=ERR_502_TC_PLC_ERROR`로 알린다. 교환 전·중 실패만 오류 코드의 숫자를 HTTP 상태로 쓴다.
- **인터페이스 없음**: `InterfaceCommandResolver`가 `Optional.empty()`를 돌려준다. 디스패처가 `ERR_404_DISPATCH_COMMAND`를 낸다.
- **커맨드 클래스가 비어 있을 때**: 범용 커맨드 사용이 꺼져 있어도 기본 커맨드를 쓴다. 레거시는 오류였다.
- **WebHook 대상**: 아직 `INTERFACE_DELIVERY` 테이블이 없다. 그래서 레거시처럼 요청 인터페이스의 `reply=true`일 때 응답 인터페이스의 `webHookNameList`를 쓴다.
- **스케줄 실행 이력**: PLC가 오류 종료 코드를 돌려주면 FAIL로 남긴다(레거시는 SUCCESS).

**framework-core 0.8.45 배포본에서 확인한 점**
- 15:18 배포본은 `AnnotatedCommandResolver`와 `ConfiguredCommandResolver`가 빈 클래스였다. 또 `MessageDispatcher.prepare`가 `Long` timestamp를 null 확인 없이 비교해 NPE가 났다.
- 15:39 배포본(같은 버전 재배포)에서 둘 다 고쳐졌다. 바이트코드로 확인했다. 다음 배포부터는 버전을 올린다.

### 4.1.1 실행 확인 (2026-10-30, 로컬 MariaDB `tc` + Redis + vplc)

tc(:8087)와 vplc(:8088)를 띄우고 실제 MC 3E 바이너리로 주고받았다.

| 확인 | 결과 |
|---|---|
| 동기 요청 `/request-and-reply` | 200, `success=true`, `resultCode=0`, data가 vplc 메모리에 쓴 값과 같음 |
| 변경 감지 | 같은 값이면 `changed=false`, 값을 바꾸면 `true` |
| 비동기 요청 `/request` | 202 + transactionId |
| 없는 opcode | 404 `ERR_404_DISPATCH_COMMAND` |
| vplc PLC 정지 | 503 `ERR_503_TC_PLC_UNAVAILABLE` (본문에 transactionId) |
| PLC 응답 없음 | 504 `ERR_504_TC_PLC_TIMEOUT` |
| WebHook + `ON_CHANGE_ONLY` | 값이 바뀔 때만 POST 1건. 실패 응답은 조건과 관계없이 전달 |
| 감사 기록 | REQUEST / RESPONSE / ERROR / DELIVERY 모두 기록 |
| 재연결 | vplc를 멈췄다 다시 시작하자 tc가 저절로 다시 연결 |

**기동·시험 중에 고친 문제**
- Quartz `jobStore.class` 지정으로 기동 실패
- Spring Data JPA 4의 null Specification 거부. 원인은 framework `SearchSpec`
- 엔티티의 `id=0` → merge 실패. 커넥터·WebHook·감사 기록이 저장되지 않았다
- 헤더 템플릿 NULL에서 NPE
- WebHook 목록이 비면 `substring(0,-1)`
- 헤더·데이터 값이 빈 문자열로 저장됨
- vplc `MemoryInfo` setter 충돌(Jackson 3)
- vplc 이력 `@Lob`이 TINYTEXT라 길이 초과

**이어서 고친 것 (같은 날, framework-core 3차 재배포본 16:32 반영)**
- **긴 글 칼럼을 모두 `@Lob @Column(columnDefinition="LONGTEXT")`로 바꿨다** (mes-dev 표준 §3.6). 대상은 감사 `MSG_JSON`(VARCHAR 255였음), 커넥터 헤더 템플릿 2개, WebHook `SAMPLE_DATA`, 연결 이력 `EVENT_JSON`, 사용자 이력 `REMARK`, vplc 이력. 감사 `RECEIVER`는 1024자로 늘렸다. 기존 DB용 SQL은 `wow-tc-backend/migration/001_long_text_longtext.sql`이고, 로컬 `tc` DB에는 적용했다.
- **ID 처리**
  - 스케줄 실행 이력 `seqNo`에 0이 들어가 저장이 실패하던 것을 고쳤다.
  - 스케줄 Job을 ID 없이 등록하면 UUID를 채번한다.
- **DTO 11개**: 쓰지 않는 `@AllArgsConstructor`/`@Builder`를 지웠다. Jackson 3가 이 생성자를 골라서, 숫자 필드를 빼고 보내면 400이 났다.
- **헤더 속성**: `PropInfo.getValueAsString`의 BYTE 값이 첫 바이트만 반복되던 것을 고쳤다.
- **MELSEC 기본 템플릿**: `subCommand`를 워드 단위 `0000`으로 바꿨다. 예전 값은 비트 단위였다.
- **결과 전달 본문**: `Content-Length`를 붙여 보낸다. 예전에는 chunked라 일부 수신 서버가 빈 본문을 받았다.
- **검색 조건**: `SearchSpecs.create` 도우미를 거치게 했다. 이 재배포본의 `SearchSpec.createSpecification`은 null 대신 `unrestricted()`에서 시작하지만, 조건 항목의 `conjType`이 비어 있으면 `switch(null)`에서 NPE가 난다. 도우미가 비어 있는 conjType을 AND로 채운다.
- **재확인**: 동기 요청 200, ID를 빼고 400바이트 넘는 sampleData로 WebHook 등록 200, conjType 없는 조건 검색 200, 조건 없는 검색 200.

**다음 framework 버전 후보**
- `SearchSpec.createSpecification`에서 `con.getConjType()`이 null이면 AND로 본다. 예: `ConjType conj = con.getConjType() != null ? con.getConjType() : ConjType.AND;`

**아직 확인하지 않은 경로**
- WRITE 인터페이스
- CALLBACK_URL 채널과 주소 허용 목록
- 스케줄 Job
- 비동기 어댑터 큐
- 커넥터 release 상태

### 4.1.2 미검증 경로 점검 (2026-10-30)

위 목록 가운데 비동기 어댑터 큐를 뺀 경로를 로컬 e2e(vplc, 콜백 수신기, 허용 목록 `http://127.0.0.1:18090/`)로 확인했다. 결과는 17개 항목 모두 통과다. 그 과정에서 **한 번도 동작한 적 없던 결함 다섯 가지**를 찾아 고쳤다.

| 결함 | 원인 | 고친 곳 |
|---|---|---|
| WRITE 가 SHORT·LONG·DOUBLE 값이면 늘 500 | JSON 숫자(Integer)를 `(Short)` 처럼 강제 변환 | `PropInfo.extractRealDataAsBytes` — 숫자형을 변환하고 범위를 검사(넘으면 400) |
| 모든 PLC 스케줄 Job 이 "interfaceName 이 없습니다"로 FAIL | Job 상세를 ID 자리에 **이름**을 넘겨 찾아 항목을 하나도 못 찾음 | `DefaultScheduleJobService.createJobDetail` |
| 성공한 스케줄 실행 이력이 하나도 저장되지 않음 | `REMARK` 가 VARCHAR(255)인데 PLC 응답 전체를 넣어 "Data too long" | `ScheduleJobExecHistoryEntity` LONGTEXT, `migration/003_schedule_hist_remark_longtext.sql`. 실패 이력에는 사유도 남긴다 |
| 스케줄 Job 삭제가 FK 오류로 실패 | JPQL 일괄 삭제가 상세(cascade)를 타지 않음 | 엔티티로 삭제(cascade) |
| 지운 스케줄 Job 이 계속 실행되고 재기동해도 남음 | DB 행만 지우고 Quartz(JDBC 저장)에서 내리지 않음 | 삭제 전에 Quartz 에서 `deleteJob` |

확인한 동작(결함이 아닌 것): 인터페이스에 전달 정의(예: WEBHOOK)가 있으면 요청의 callback 은 `CALLBACK_URL` 정의가 있을 때만 쓴다(§4.2의 규칙 그대로).
덧붙여 WebHook 을 지워도 메모리(`WebHookManager`)에 남아 재기동 전까지 결과가 계속 그 주소로 가던 문제도 고쳤다.

### 4.2 결과 전달 정의 (`INTERFACE_DELIVERY`)

인터페이스마다 결과를 어디로, 언제 보낼지 정의한다. 새 테이블이라 mes-dev 표준을 따른다. PK는 문자열이고, 인터페이스는 연관관계 대신 이름으로 참조한다.

| 칸 | 내용 |
|---|---|
| `ID` | 영문 소문자·점 계층. 비워서 등록하면 `{인터페이스}.{유형}.{대상}`으로 만든다 |
| `INTERFACE_NAME` | 요청 인터페이스(opcode). 인덱스 |
| `DELIVERY_TYPE` | `CALLBACK_URL`, `WEBHOOK` (`DB_TABLE`은 6단계) |
| `TARGET_NAME` | WEBHOOK이면 WebHook 이름. CALLBACK_URL은 빈 값(주소는 요청이 준다) |
| `DELIVER_ON` | `ALL`, `SUCCESS`, `FAILURE` — 성공·실패 중 언제 보낼지 |
| `ON_CHANGE_ONLY` | PLC 응답 원본이 직전과 달라졌을 때만 보냄. 실패 응답에는 적용하지 않는다 |
| `IS_USE` | 사용 여부 |

- 유니크 제약: (인터페이스, 유형, 대상)
- **대상 결정 규칙**
  - 인터페이스에 사용 중인 정의가 **하나라도 있으면 정의대로만** 보낸다.
  - 이때 요청의 callback 주소는 `CALLBACK_URL` 정의가 있을 때만 쓴다. 정의 없이 callback을 요청하면 보내지 않고 경고 로그를 남긴다.
  - 정의가 **하나도 없으면 레거시 방식**이다. 요청의 callback 주소와, `reply=true`일 때 응답 인터페이스의 `webHookNameList`를 쓴다.
- **관리 API** (`/api/v1/ifsvc/delivery`)
  - `GET ?interfaceName=`: 목록
  - `GET /{id}`: 한 건
  - `POST`: 등록, 201
  - `PUT /{id}`: 수정. 인터페이스와 id는 바꿀 수 없다
  - `DELETE /{id}`: 삭제, 204
- **등록 검증**
  - 인터페이스가 있어야 한다(`ERR_404`).
  - WEBHOOK이면 WebHook이 있어야 한다(`ERR_404`).
  - 같은 대상이나 같은 ID가 있으면 `ERR_409`.
- 테이블은 `ddl-auto=update`로 기동할 때 만들어진다.

## 5. 아직 정하지 않은 것

1. **DB Insert 대상**: tc 자신의 DB인가, MES DB 같은 외부 DB인가? 응답 1건이 행 1개인가?
2. **비동기 전달 실패 시 재시도**: 재시도와 실패 이력(outbox)까지 필요한가, 감사 로그에 실패를 남기는 것으로 충분한가?
3. **요청 본문의 `sync`, `pushResult` 필드**: 외부 시스템이 이미 이 필드를 보내고 있는가? 그렇다면 한동안은 받아 주되 무시한다.
