# 기존 tc-backend 분석 (tc, vplc 모듈)

- 분석 대상: `D:\Source\Workspaces\tc\tc-backend` (Maven 멀티모듈 `control-parent`)
- 범위: `tc` 모듈(Tool Control, 205개 파일, 약 14,800줄), `vplc` 모듈(가상 PLC, 93개 파일, 약 6,800줄)
- 분석일: 2026-10-30
- 아래 파일 경로는 각 모듈의 `src/main/java/com/mymes/equip/{tc|vplc}/` 기준이다.
- ✅ 표시는 소스를 직접 열어 확인한 항목이다. 나머지는 코드 분석 결과이며, 수정 전에 다시 확인해야 한다.

## 1. 기술 스택

| 항목 | 내용 |
|---|---|
| 기반 | Spring Boot 3.3.3, Java 17, Maven |
| DB | MariaDB, JPA (`ddl-auto=update`). 스키마 마이그레이션 도구 없음 |
| 캐시 | Redis. refresh token과 직전 PLC 응답 저장용 (HTTP 세션은 사용하지 않음) |
| 스케줄 | Quartz. 작업 저장소를 DB에 둔 클러스터 구성 |
| 인증 | Spring Security, JWT (jjwt 0.12) |
| 통신 | Netty (TCP), RestTemplate (WebHook) |
| 운영 | actuator, Spring Boot Admin client (:8082), springdoc |
| 포트 | tc 8080, vplc 8088 |

## 2. 전체 구성

```
MES / 외부 시스템 ──REST, WebHook, pushUrl──┐
브라우저 UI ──────REST, SSE────────────────┤
                                     [tc :8080]  Tool Control
                       Quartz 스케줄 → Dispatcher → Command
                       → MELSEC 어댑터(Netty) ──TCP, MC 3E──→ PLC 또는 [vplc :8088]
                       → 감사 로그 저장(DB) → SSE 알림 / WebHook 전송
```

### 2.1 tc 처리 흐름

1. Quartz 트리거가 `ScheduleJob.execute`를 실행한다 (`schedule/ScheduleJob.java:43`).
2. `PlcGeneralScheduleJob`이 JobDataMap의 interfaceName으로 `TcMessageRequest`를 만든다 (`schedule/jobs/PlcGeneralScheduleJob.java:42`).
3. `MessageDispatcher.dispatch`가 `MessageCommandFactory`를 거쳐 `GenericMessageCommand.doExecution`을 실행한다.
   - 인터페이스 정의(HEADER_PROP, DATA_PROP)를 조회해 PLC 요청을 만든다.
   - `PlcAdapterManager`가 MELSEC 어댑터를 찾아 Netty 채널로 요청을 보낸다.
   - Redis에 저장된 직전 응답과 비교해 값이 바뀌었는지 확인한다(`checkIfChange`).
4. 요청과 응답을 감사 로그로 저장하고, SSE로 `AUDIT_MESSAGE`를 보낸다.
5. 응답 인터페이스에 등록된 WebHook마다 POST를 보낸다.

REST 진입점인 `/api/v1/ifsvc/request`와 `/api/v1/ifsvc/request-and-reply`도 같은 Dispatcher 경로를 탄다.

### 2.2 vplc 처리 흐름

```
tc --TCP--> Netty (PLC마다 NioEventLoopGroup)
  MessageDecoder → MessageFrameHandler → VirtualPlcMessageDelegate
    → Postbox (PLC마다 스레드 1개, 50ms 폴링 큐)
    → VirtualProgrammableController.read/write → Memory(short[20000])
    → MessageResponse → ClientChannel.writeAndFlush → tc
  쓰기가 일어나면 MemoryChangedEvent를 SSE `vplc_mem_value_updated`로 보낸다.
```

- 기동하면 `VirtualPlcLoader`가 DB에서 PLC 목록을 읽어 올리고, RUNNING 상태였던 PLC는 바로 시작한다.
- PLC마다 `startPort`부터 `portCount`개 포트를 연다(기본 12345). 포트별로 연결 수를 제한한다.
- 메모리 값은 DB에 저장하지 않는다. 재시작하면 모두 0으로 초기화된다.

## 3. 도메인 모델

### 3.1 tc

- **JPA 테이블**
  - `INTERFACE`: name(=opcode), plcName, replyName, IF_TYPE(READ/WRITE), DATA_LEN, CMD_CLASS_NAME
  - `HEADER_PROP`, `DATA_PROP`: INTERFACE와 1:N 관계. 필드별 order, length, type, 값을 가진다.
  - `PLC_CONNECTOR`: hostIp, ports, vips, adapterClassName, frameFormat, 게시 상태, 운영 상태
  - `PLC_CONNECTION_HIST`
  - `SCHEDULE_JOB`, `SCHEDULE_JOB_DETAIL`(1:N), `SCHEDULE_JOB_EXEC_HIST`
  - `EXCHANGED_MESSAGE_AUDIT`, `WEBHOOK`, `USER`, `USER_HIST`
- **Redis**: `JwRefreshTokenEntity`, `PlcResponseEntity`(직전 PLC 응답)
- **헤더 템플릿**: `resources/templates/header_props/`의 JSON(3E 바이너리/ASCII 기본값)은 UI에 기본값을 보여주는 데만 쓴다. 실제 프레임은 DB의 HEADER_PROP으로 만든다.

### 3.2 vplc

- **JPA 테이블**
  - `VPLC`: name, status, frameFormat, ports, memTypes
  - `VPLC_HISTORY`
  - `CONNECTION`: (vplcId, port)
  - `CONNECTED_CLIENT`: (vplcId, port, clientIp)
  - `CONNECTED_CLIENT_HIST`
- **디바이스 코드 표**: `machine/Types.java`의 `MemoryType`에 SM, SD, X, Y, M, L, F, V, B, D, W, TS/TC/TN, R, ZR 등의 코드와 주소 범위가 정의되어 있다.

## 4. PLC 통신 (MELSEC MC 프로토콜)

| 항목 | 현재 상태 |
|---|---|
| 프레임 | 3E만 지원. vplc는 4E를 거부한다 |
| 명령 | 워드 단위 일괄 읽기(0401)와 쓰기(1401)만 지원. 비트 단위 접근 없음 |
| 바이너리 | tc와 vplc 사이에 호환됨 (요청 필드 순서와 응답 헤더 D000 일치) |
| ASCII | **tc, vplc 양쪽 모두 동작하지 않음** |
| 채널 | tc는 PLC 하나에 채널 여러 개를 열고 라운드로빈으로 고른다. 하지만 `synchronized` 블록 안에서 기다리므로 요청이 하나씩만 처리된다 |
| 응답 대기 | 50ms마다 확인하며 최대 20초를 기다림 (하드코딩) |
| 재시도 | 처음 연결할 때만 `plc.connection.retry.*` 설정대로 재시도한다. 연결이 끊긴 뒤 자동 재연결은 없음 |
| vplc 명령 판별 | `command2==0x14`이면 쓰기, 그 밖의 명령은 모두 읽기로 처리 ✅ |

## 5. 스케줄링

- **Job 종류**
  - `PlcGeneralScheduleJob`: PLC를 읽거나 쓴 뒤 WebHook을 보낸다.
  - `DataTableFlashJob`: 오래된 이력을 지운다. SERVICE_BEAN 방식만 구현되어 있다.
  - `PlcConnectorSpyScheduleJob`: 빈 스텁이다.
- **등록**: DB의 `SCHEDULE_JOB`을 읽어 CRON 또는 SIMPLE 트리거로 Quartz에 등록한다. 기동하면 모든 job을 지우고 다시 등록한다.
- **설정 충돌**: Quartz 설정이 `quartz.properties`, `application.properties`, `ScheduleConfig` 세 곳에 흩어져 있다. 실제로는 `ScheduleConfig.java:51`의 `setDataSource`가 적용되어 다른 두 곳의 DB 설정은 무시된다 ✅.
- **이중화 미구현**: `oper.duplication.mode=active-standby`를 코드 어디에서도 읽지 않는다 ✅. 노드가 여러 개면 모두 같은 PLC에 연결하고, 기동할 때마다 job을 다시 등록한다.

## 6. 외부 인터페이스

### 6.1 tc

| 경로 | 내용 |
|---|---|
| `/auth` | `login`, `reissue`(refresh token 쿠키), `logout` |
| `/api/v1/ifsvc` | `request`(응답 없음, pushUrl로 결과 전송 가능), `request-and-reply`(동기 응답) |
| `/api/v1/ifsvc/management` | 인터페이스 등록, 수정, 삭제, 사용 여부 변경, 조회, 헤더 템플릿 조회 |
| `/api/v1/ifsvc/audit` | 감사 로그 조회, 삭제 |
| `/api/v1/plc-connector` | 커넥터 CRUD, publish/release/pause/resume, 연결 테스트, 연결 이력 |
| `/api/v1/schedule-job/management` | 스케줄 등록, 수정, 삭제, 조회, 실행 이력 |
| `/api/v1/schedule-job/monitoring` | 실행 중인 job 조회, 전체 또는 개별 pause/resume/reschedule |
| `/api/v1/webhook` | WebHook CRUD, URL 확인 |
| SSE | `GET /connect/{userId}` (타임아웃 60초) |
| 화면 라우팅 | `/interface`, `/plc`, `/webhook`, `/schedule/**` |

### 6.2 vplc

베이스 경로는 `/api/v1/vplc`이다.

| 경로 | 내용 |
|---|---|
| `management/*` | 가상 PLC 등록, 수정, 포트 변경, publish, release, 삭제, 조회, 이력 |
| `control/*` | start, stop, pause, resume |
| `manipulation/*` | 메모리 읽기, 쓰기, 전체 초기화 |
| SSE | `GET /connect/{userId}` |

**공통 문제**: delete, publish처럼 상태를 바꾸는 작업을 GET으로 처리하는 엔드포인트가 많다 ✅ — tc 는 2026-10-31(#9), vplc 도 같은 날 DELETE·POST 로 바꿨다.

## 7. 보안과 인증

- **JWT**
  - access token은 `Authorization` 헤더로 보낸다.
  - refresh token은 HttpOnly 쿠키와 Redis에 저장한다.
  - 세션 정책은 STATELESS다.
- **인가 규칙**: `/api/v1/**`에 `hasAnyRole("ADMIN","MANAGER","USER")`를 요구한다. 그런데 `UserRole`에는 USER가 없고 EMPLOYEE와 CLIENT만 있다 ✅.
- **미구현**: 사용자 관리 API(`UserController`)는 주석 처리되어 있고, Keycloak 설정도 전부 주석이다.
- **vplc**: 모든 요청이 인증 없이 허용된다(`permitAll`) ✅. CSRF도 꺼져 있다.

## 8. 품질 이슈

### 8.1 치명 / 높음

| # | 모듈 | 위치 | 문제 |
|---|---|---|---|
| 1 | 공통 | `application.properties` | DB, Redis 비밀번호와 JWT 비밀키가 평문으로 들어 있고, DB는 root 계정으로 접속한다 |
| 2 | 공통 | `application.properties` | actuator가 모든 엔드포인트(`*`)를 공개하고 `shutdown`까지 켜져 있다 |
| 3 | tc | `dispatcher/GenericMessageCommand.java:86` | `byte[] before=null;` 상태에서 `before[i]`를 읽는다. 같은 opcode의 응답이 두 번째부터 항상 NPE로 실패한다 ✅ |
| 4 | tc | `plc/connector/melsec/MelsecProgrammableControllerAdapter.java:76` | `reqCallbacks`를 초기화하지 않아 비동기 요청 경로가 NPE로 실패한다 ✅ |
| 5 | tc | `user/UserRole.java`, `security/SecurityConfig.java:61` | 역할 불일치로 EMPLOYEE와 CLIENT는 항상 403을 받는다 ✅ |
| 6 | tc | `plc/connector/melsec/MelsecPlcRequest.java:136` | 명령 검증을 `&&`로 해서 잘못된 명령을 거의 걸러내지 못한다 ✅ |
| 7 | tc | `dispatcher/MessageCommand.java:72` 등 | pushUrl, WebHook URL, `test-connection/byIpAddress`에서 대상 주소를 검증하지 않아 SSRF가 가능하다 |
| 8 | tc | `schedule/impl/ScheduleJobServiceImpl.java:712` | 값을 String만 저장하는 Quartz 설정(`useProperties=true`)인데 작업 데이터에 Bean이나 List, Map을 넣는다 |
| 9 | tc | `endpoints/AuthController.java`, `auth/impl/*` | 쿠키 maxAge 계산에서 int 오버플로, refresh token 만료에 access 만료값 사용, Redis TTL에 절대 시각 사용, 쿠키가 없으면 NPE |
| 10 | vplc | `MessageError.java` | `toByteArray()`가 null을 반환해 오류 응답을 보내지 못하고, tc는 타임아웃만 겪는다 ✅ |
| 11 | vplc | `machine/Memory.java:99-121` | `read`/`write`에 주소 범위 검사가 없다 ✅ |
| 12 | 공통 | ASCII 코덱 | 헤더 키 오타, 길이 필드 인코딩 오류, 응답 데이터 누락 등으로 ASCII 모드가 동작하지 않는다 |

### 8.2 중간 (동시성, 리소스)

- 호출할 때마다 `Executors.newCachedThreadPool()`을 새로 만들고 정리하지 않는다.
  - tc: `MessageDispatcher`, `PlcGeneralScheduleJob`
  - vplc: `Memory.write` ✅
- 재시도할 때마다 Netty `NioEventLoopGroup`을 새로 만들고 기존 것을 정리하지 않는다.
- 정적 싱글턴을 지연 생성할 때 동기화가 없고, 안에서 일반 HashMap을 쓰는데 여러 스레드가 동시에 접근한다.
  - 해당 클래스: `PlcAdapterManager`, `WebHookManager`, `SseEmitterManager`, `ClientChannel` 등
- vplc는 Netty IO 스레드에서 DB 저장(블로킹 JDBC)을 한다.
- Job에 `@DisallowConcurrentExecution`이 없어 같은 PLC 요청이 겹칠 수 있다.
- SIMPLE 트리거의 repeatCount가 5로 하드코딩되어 있다.
- `DataTableFlashJob`의 `savingDays*24*60*60*1000` 계산이 int라서 25일 이상이면 오버플로가 난다.
- SSE emitter를 목록에서 지우지 않아 죽은 연결이 계속 쌓인다.

### 8.3 낮음 (죽은 코드, 스텁, 하드코딩)

- **동작하지 않는 등록**: `@Command` 어노테이션으로 커맨드를 등록하는 기능이 비교 대상을 잘못 잡아 절대 매칭되지 않는다 (`MessageCommandFactory.java:182`).
- **죽은 코드와 스텁**
  - `check()`, `testConnection()`이 항상 true를 반환한다.
  - `*ByVplcName` 메서드가 null을 반환한다.
  - vplc의 `push/` 패키지는 전부 주석이다.
- **잘못된 위치의 코드**: 테스트용 main 클래스(`DispatcherTest`, `NettyByteBufTest` 등)가 `src/main`에 있다.
- **강제 종료**: `System.exit(0)`을 여러 곳에서 호출한다.
- **진단하기 어려운 오류 처리**: 메시지 없는 예외(`new ToolControlException()`)와 빈 `log.warn("")`이 많다.
- **불필요한 의존성**: Boot 3과 호환되지 않는 springfox가 남아 있다.
- **하드코딩**: CORS(localhost:3000), 응답 타임아웃 20초, `show-sql=true`
- **테스트**: 두 모듈 모두 `assertTrue(true)` 하나뿐이라 사실상 0개다.

## 9. wow-tc에 반영할 점

### 9.1 가져갈 것

- **도메인 개념**: 인터페이스(opcode)와 헤더/데이터 형식(order, length, type)
- **테이블 구조**: PLC 커넥터, 스케줄 정의, 실행 이력, 감사 로그, 연결 이력
- **헤더 템플릿과 디바이스 코드 표**
  - 3E 바이너리 헤더 템플릿 JSON
  - 디바이스 코드 표(vplc `Types.java`). 모르는 코드를 D로 처리하는 부분은 제거한다.
- **REST 리소스 구분**: interface, plc-connector, schedule, webhook, audit
- **SSE 알림**: 감사 로그와 메모리 변경을 SSE로 알리는 방식
- **테스트 구성**: 가상 PLC를 통합 테스트 상대로 쓰는 방식

### 9.2 다시 설계할 것

- **MC 프로토콜 처리**
  - 3E/4E와 바이너리/ASCII를 하나의 인코더·디코더로 정리한다.
  - 비트 단위 접근, 표준 오류 코드(end code), 길이 검증을 넣는다.
  - 프레임 분리는 `LengthFieldBasedFrameDecoder`로 하고, 요청과 응답은 `CompletableFuture`로 짝을 맞춘다. 4E 프레임이면 시리얼 번호를 쓴다.
  - 연결이 끊기면 백오프를 두고 자동으로 다시 연결한다.
  - 프로토콜 테스트 데이터를 만든다.
- **디바이스 메모리 (가상 PLC)**
  - 비트와 워드를 구분하고, 범위에 맞춰 크기를 정한다.
  - 동시 접근을 제어하고, 필요하면 스냅샷을 저장한다.
- **이중화**: active-standby를 실제로 구현한다. DB 락이나 Redis로 리더를 정해 한 노드만 PLC에 연결하게 하고, 커넥터 상태 변경을 모든 노드에 전파한다.
- **구조**
  - 정적 싱글턴과 서비스 로케이터를 Spring Bean(DI)으로 바꾼다.
  - 스레드 풀은 공용 `TaskExecutor` 하나를 쓴다.
  - WebHook은 outbox 패턴으로 보내고, 타임아웃과 재시도를 둔다.
- **보안**
  - 비밀값을 설정 파일 밖(환경 변수, `.env`)으로 옮긴다.
  - 역할 모델과 인가 규칙을 맞추고, actuator 공개 범위를 제한한다.
  - 전송 대상 URL은 허용 목록으로 관리한다.
  - 상태를 바꾸는 작업은 POST, PUT, DELETE로 바꾼다.
- **운영**
  - 스키마는 Flyway로 관리하고, Quartz 설정은 한 곳으로 모은다.
  - 단위 테스트와 통합 테스트를 작성한다.
