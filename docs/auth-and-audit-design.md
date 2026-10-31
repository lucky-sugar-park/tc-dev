# tc 인증·권한·감사 설계

> 상태: 설계안 (2026-10-30). 구현 전이다.
> 대상: `wow-tc-backend/tc`. framework-core 0.8.45 기준.

## 1. 배경과 결정

tc를 부르는 주체는 성격이 다른 두 종류다.

| 주체 | 수 | 성격 | 인증 |
|---|---|---|---|
| 사용자(운영자·관리자) | 소수(1~수 명) | 화면(SPA)에서 PLC·인터페이스·스케줄을 설정하고 제어한다 | 아이디·비밀번호 로그인 → JWT(framework) |
| 타 시스템(MES 등) | 여러 개, 늘어남 | `/api/v1/ifsvc/request*`로 PLC 값을 읽고 쓴다 | API 키 |

검토한 끝에 내린 결정은 다음과 같다.

- **identity 모듈에 의존하지 않는다.**
  - identity의 `sec.auth`는 identity-user의 `UserInfo`, `UserService`, `UserHistoryService`, `UserCodes`에 깊이 묶여 있다.
  - 쓸 만한 핵심(JWT 발급·검증, `CustomAuthenticationProvider`, refresh 토큰 저장소, `@Requires`)은 이미 framework-core에 있고, tc는 이를 쓰고 있다.
  - identity에서 검증된 개선점만 골라 옮긴다(§3.6).
- **사용자 테이블은 남기되 줄인다.**
  - 가입 요청·승인 흐름과 직원 정보 필드는 뺀다.
  - **사용 이력은 유지한다.**
- **JWT는 유지한다.**
  - framework `SecurityConfig`(STATELESS + `JwtFilter`)가 이미 적용돼 있다. 레거시 프론트도 refresh 쿠키로 재발급하는 흐름을 갖고 있다.
  - JWT를 걷어내면 framework와 갈라지는 비용이 더 크다.
- **누가 어떤 설정을 바꿨는지 남기는 설정 변경 이력을 새로 둔다(§5).**
  - 기존 `USER_HIST`는 로그인·로그아웃 같은 사용자 자신의 행위만 담는다.
  - 엔티티의 `updatedBy`는 마지막으로 바꾼 사람 한 명만 남긴다.
- **재기동 없이 운영한다.** tc를 재기동하면 MES 등 tc를 부르는 시스템의 서비스가 중단될 수 있다.
  - 사용자, API 키, 권한, 설정 변경은 모두 런타임에 처리한다.
  - 재기동이 필요한 항목은 §7에 명시한다.

## 2. 전체 구조

```
                ┌─ machineApiChain (@Order 1) ─────────────────────────────┐
POST /api/v1/ifsvc/request             │ ApiKeyAuthenticationFilter (X-API-Key)  │
POST /api/v1/ifsvc/request-and-reply ──┤ 또는 Bearer JWT (화면에서 시험 호출)       │──► DispatcherController
                                       │ hasAnyRole(CLIENT, ADMIN, OPERATOR)      │      └─ ClientOpcodeGuard (DispatchInterceptor)
                └──────────────────────────────────────────────────────────┘
                ┌─ framework SecurityConfig.filterChain (그 외 전부) ─────────┐
/api/auth/**, /api/v1/** (관리) ──────┤ JwtFilter → /api/** authenticated        │──► 관리 컨트롤러
                                       │ PermissionEnforcementInterceptor(@Requires)│      └─ ConfigChangeRecorder
                └──────────────────────────────────────────────────────────┘
```

- **API 키가 통하는 곳은 PLC 요청 두 경로뿐이다.** `/api/v1/ifsvc/` 아래에도 audit, delivery, management처럼 관리용 API가 있으므로, 경로 접두어로 열지 않고 **두 경로를 정확히** 지정한다.
- **권한 검사는 framework의 `@Requires` 체계를 쓴다.** tc는 `PermissionChecker` 구현 하나만 제공한다(§4).

## 3. 사용자

### 3.1 테이블 `USER` (기존 테이블을 줄인다)

| 칼럼 | 유지·추가·삭제 | 설명 |
|---|---|---|
| ID (PK) | 유지 | 로그인 아이디 |
| PASSWORD | 유지 | BCrypt 해시 |
| NAME | 유지 | 표시 이름 |
| EMAIL | 유지 | 연락용(선택). 로그인이나 인증에는 쓰지 않는다 |
| ROLES | 유지 | `ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_VIEWER` 중 하나 이상(§4.1) |
| ENABLED | 유지(의미 변경) | 예전 의미는 "가입 승인됨", 이제는 "사용 중". false이면 로그인과 재발급을 거부한다 |
| PASSWORD_CHANGED_AT | 추가 | 비밀번호를 마지막으로 바꾼 시각 |
| PASSWORD_MUST_CHANGE | 추가 | 관리자가 초기화한 뒤 첫 로그인에서 변경을 강제한다 |
| LAST_LOGIN_AT, LAST_LOGIN_IP | 추가 | 마지막 로그인. 예전 `logined`·`logined_ip`를 대신한다 |
| phone, extra_info | **삭제** | 직원 정보 |
| logined, logined_ip | **삭제** | 사용자 행에 로그인 "상태"를 두면 토큰이 여러 개일 때 틀린다. 로그인 사실은 이력에 남는다 |

`CREATED_BY`, `UPDATED_BY` 같은 감사 칼럼은 `PersistentEntity`가 그대로 채운다.

### 3.2 최초 관리자 생성(bootstrap)

- 기동할 때 **`USER` 테이블이 비어 있을 때만** 설정값으로 첫 관리자를 만든다.
  ```yaml
  tc:
    bootstrap:
      admin-id: ${TC_BOOTSTRAP_ADMIN_ID:}
      admin-password-hash: ${TC_BOOTSTRAP_ADMIN_PASSWORD_HASH:}   # BCrypt 해시. 평문 금지
  ```
- 생성한 사용자는 `PASSWORD_MUST_CHANGE=true`로 둔다.
- 테이블에 한 명이라도 있으면 이 설정은 읽지 않는다. 설정을 바꿔도 아무 일이 일어나지 않으니 오해할 여지가 없다.
- 평문 비밀번호를 로그에 찍어 알려 주는 방식은 쓰지 않는다. 로그가 수집되는 곳으로 비밀번호가 새기 때문이다.

### 3.3 사용자 관리 API (`ROLE_ADMIN` 전용)

| 메서드·경로 | 동작 |
|---|---|
| `GET /api/v1/users` | 목록 |
| `GET /api/v1/users/{id}` | 조회. 비밀번호 해시는 응답에 넣지 않는다 |
| `POST /api/v1/users` | 생성. 관리자가 초기 비밀번호를 정하고 `PASSWORD_MUST_CHANGE=true`로 둔다 |
| `PUT /api/v1/users/{id}` | 이름, 이메일, 역할 변경 |
| `POST /api/v1/users/{id}/enable`, `/disable` | 사용 중지와 재개. 중지할 때 그 사용자의 refresh 토큰을 모두 지운다(`findAllByUserId`) |
| `POST /api/v1/users/{id}/password-reset` | 비밀번호 초기화. `PASSWORD_MUST_CHANGE=true`로 둔다 |
| `DELETE /api/v1/users/{id}` | 삭제. 이력은 USER_ID 문자열로 남으므로 지워지지 않는다. 운영에서는 중지를 권장한다 |

본인용 API(로그인한 모든 사용자):

| 메서드·경로 | 동작 |
|---|---|
| `GET /api/v1/me` | 내 정보, 역할, `PASSWORD_MUST_CHANGE` |
| `PUT /api/v1/me/password` | 현재 비밀번호를 확인한 뒤 변경 |

보호 규칙:

- **마지막 활성 관리자는 지킨다.** 활성 `ROLE_ADMIN`이 한 명만 남으면 그 사람을 중지·삭제하거나 관리자 역할을 빼는 요청을 거부한다(409).
- **자기 자신은 중지·삭제할 수 없다.**
- **비밀번호 규칙**: 10자 이상, 아이디와 같지 않을 것, 직전 비밀번호와 같지 않을 것.
- **`PASSWORD_MUST_CHANGE=true`인 사용자**: 로그인은 성공한다. 다만 `/api/v1/me`와 `/api/v1/me/password`, 로그아웃 외의 API는 403(`ERR_403_TC_PASSWORD_CHANGE_REQUIRED`)을 받는다.

### 3.4 로그인 실패 제한 (계정 잠금 없음)

- **계정은 잠그지 않는다.** 사용자가 소수이므로, 남이 일부러 비밀번호를 틀려 유일한 관리자를 잠그는 공격 수단이 될 수 있다.
- 대신 Redis 카운터로 제한한다.
  - `tc:login-fail:user:{id}`, `tc:login-fail:ip:{ip}`
  - 15분 안에 5회 실패하면 15분 동안 429를 낸다.
- **429 응답 문구는 계정 존재 여부와 관계없이 같다.** framework `CustomAuthenticationProvider`의 S-08 원칙(실패 문구 통일)과 맞춘다.
- 로그인에 성공하면 그 아이디의 카운터를 지운다.
- 운영 옵션(횟수, 시간)은 `tc.security.login-throttle.*`에 둔다. 런타임 변경이 필요하면 §7의 운영 옵션 표로 옮긴다.

### 3.5 사용 이력 `USER_HIST` (유지)

사용자 자신의 행위를 남긴다. 기존 칼럼 `USER_ID`, `OPER`, `TIMESTAMP`, `IP_ADDR`, `REMARK`를 그대로 쓴다.

| OPER | 시점 |
|---|---|
| `LOGIN` | 로그인 성공 |
| `LOGIN_FAIL` | 비밀번호 틀림(없는 아이디는 기록하지 않음) |
| `LOGIN_THROTTLED` | §3.4 제한에 걸림 |
| `LOGOUT` | 로그아웃 |
| `REISSUE_DENIED` | 중지된 사용자의 재발급 거부 |
| `PASSWORD_CHANGE` | 본인 비밀번호 변경 |

관리자가 다른 사용자를 바꾼 일(생성, 역할 변경, 중지, 초기화)은 **설정 변경 이력**(§5)에 남긴다. 대상 종류는 `USER`다.

### 3.6 identity에서 옮겨 올 개선점

| 항목 | 출처 | tc 현재 |
|---|---|---|
| 재발급할 때 중지된 사용자는 거부하고 `REISSUE_DENIED`를 기록 | identity `DefaultAuthService.reissue` | 없음 |
| 삭제된 사용자의 토큰으로 로그아웃해도 500을 내지 않음(토큰 제거가 본질) | identity 리뷰 I-2 | 확인 필요 |
| refresh 토큰을 회전하고, 저장된 값과 다르면 거부 | identity | 있음 |

옮긴 코드에는 출처를 주석으로 남긴다. identity가 바뀌었을 때 따라가기 위해서다.

### 3.7 정리 대상

- **`UserService`에서 뺄 것**: `requestUserRegistion`, `approveUserRegistration`, `releaseUserRegistration`, `findUserByEmail`
- **레거시 화면에서 뺄 것**: 가입 요청 메뉴(`/user/register/request`), 가입 승인 메뉴(`/user/register/approval`)

## 4. 권한

### 4.1 역할 (제안)

| 역할 | 할 수 있는 것 |
|---|---|
| `ROLE_ADMIN` | 전부. 사용자와 API 클라이언트 관리 포함 |
| `ROLE_OPERATOR` | PLC, 인터페이스, 헤더·데이터, WebHook, 전달 정의, 스케줄 Job의 등록·수정·삭제·제어. PLC 요청 시험 호출 |
| `ROLE_VIEWER` | 조회만. 감사, 이력, 모니터링 |
| `ROLE_CLIENT` | API 키 주체 전용. PLC 요청 두 경로만 |

> 결정 필요: 사용자가 1~2명뿐이라면 `ROLE_ADMIN` 하나로 시작하고, 나머지는 필요할 때 켜도 된다. 구조는 같다.

### 4.2 `TcPermissionChecker`

- framework `AuthzWebConfig`는 `PermissionChecker` 빈이 있을 때만 `@Requires` 인터셉터를 등록한다. 지금 tc에는 이 빈이 없어서 `@Requires`가 효과가 없다.
- tc는 역할 → 자원·동작 표 하나로 이 포트를 구현한다.
  - 사용자 수가 적으니 DB나 화면으로 관리하는 권한 매트릭스는 두지 않는다.
  - 역할은 `SecurityContext`의 권한(JWT `auth` 클레임)에서 읽는다. 요청마다 DB를 조회하지 않는다.
- **자원 코드**

  | 자원 코드 | 대상 |
  |---|---|
  | `tc.plc` | PLC 커넥터 |
  | `tc.interface` | 인터페이스 |
  | `tc.webhook` | WebHook |
  | `tc.delivery` | 전달 정의 |
  | `tc.schedule` | 스케줄 Job |
  | `tc.audit` | 감사·이력 |
  | `tc.user` | 사용자 |
  | `tc.apiclient` | API 클라이언트 |
  | `action.tc.dispatch` | PLC 요청 |
  | `action.tc.redeliver` | 결과 재전송 — 관리자만 ([redelivery-design.md](redelivery-design.md) R7) |

- **구현 계약**: 모르는 사용자나 자원이면 예외를 던지지 않고 거부(`Decision.deny`)를 돌려준다. 예외를 던지면 403이 아니라 500이 된다.
- **`PermissionModeSource`**: 처음에는 `AUDIT` 모드로 둔다. 선언이 빠진 쓰기 API를 기록으로 찾아 선언을 채운 뒤 `ENFORCE`로 바꾼다. 모드는 운영 옵션으로 두어 재기동 없이 바꾼다.

### 4.3 주의: 상태를 바꾸는 GET

tc의 관리 API 상당수가 **GET으로 상태를 바꾼다.** 예를 들면 다음과 같다.

- `GET /delete/{id}`
- `GET /control/release/{id}`, `/pauseAllScheduleJobs`
- `GET /update/apply-use/{id}/{use}`

`@Requires`를 클래스에 붙이면 GET은 검사하지 않는다(framework 결정 20-5). 그래서 이 메서드들에는 **메서드 단위로 `@Requires(action='D'|'U'|'X')`를 명시**해야 한다. 그러지 않으면 `ROLE_VIEWER`가 삭제나 제어를 할 수 있게 된다.

- 근본 해결은 POST·PUT·DELETE로 바꾸는 것이다. **2026-10-31 반영**(결정 D5): 29개 엔드포인트를 경로는 그대로 두고 삭제는 DELETE, 제어·사용 변경·연결 시험은 POST 로 바꿨다. 예전 GET 은 405 다. 메서드 단위 `@Requires` 는 그대로 둔다(POST 는 클래스 선언이 C 로 보므로 U·X 명시가 계속 필요하다).

## 5. 설정 변경 이력 `CONFIG_CHANGE_HIST`

### 5.1 테이블

| 칼럼 | 설명 |
|---|---|
| ID (PK, String) | UUID |
| CHANGED_AT | 시각 |
| ACTOR_TYPE | `USER`, `API_CLIENT`, `SYSTEM`(스케줄 등 내부) |
| ACTOR_ID | 사용자 ID, clientId, 또는 `system` |
| ACTOR_IP | 요청 IP. framework `ClientAddressResolver`를 따른다 |
| TARGET_TYPE | `PLC_CONNECTOR`, `INTERFACE`, `HEADER`, `DATA`, `WEBHOOK`, `DELIVERY`, `SCHEDULE_JOB`, `USER`, `API_CLIENT`, `API_KEY`, `OPTION` |
| TARGET_ID | 대상 식별자 |
| TARGET_NAME | 대상 이름. 삭제된 뒤에도 무엇이었는지 읽을 수 있게 남긴다 |
| ACTION | `CREATE`, `UPDATE`, `DELETE`, `ENABLE`, `DISABLE`, `CONTROL` |
| DETAIL | `CONTROL`의 세부 동작(`publish`, `release`, `pause`, `resume`, `reload`, `password-reset`, `key-issue`, `key-revoke` 등) |
| BEFORE_JSON, AFTER_JSON | 변경 전·후. LONGTEXT |
| TRANSACTION_ID | 같은 요청에서 생긴 여러 건을 묶는다(일괄 삭제 등) |

색인: `(TARGET_TYPE, TARGET_ID, CHANGED_AT)`, `(ACTOR_ID, CHANGED_AT)`, `(CHANGED_AT)`.

### 5.2 기록 방법

- **한 곳의 기록기 `ConfigChangeRecorder.record(targetType, targetId, targetName, action, detail, before, after)`를 둔다.**
- **서비스 계층의 저장·삭제·제어 지점에서 부른다.** 컨트롤러에서 부르지 않는 이유는, 컨트롤러를 거치지 않은 변경(일괄 처리, 내부 호출)이 빠지기 때문이다.
- **같은 트랜잭션**(`Propagation.MANDATORY`, 없으면 `REQUIRED`)으로 기록한다. 변경은 됐는데 이력이 없거나, 이력은 있는데 변경이 롤백되는 일을 막는다.
- **행위자**는 `SecurityContextUsers.currentUserId()`와 인증 종류로 정한다. 인증이 없는 내부 호출은 `SYSTEM`이다.
- **비밀값은 가린다.** 비밀번호 해시와 API 키 해시는 JSON에 넣기 전에 `"***"`로 바꾼다. 가릴 필드는 `@Masked` 같은 표시나 이름 목록으로 정한다.
- **변경이 없으면 남기지 않는다.** UPDATE인데 전·후 JSON이 같으면 기록하지 않는다.

### 5.3 조회 API (`ROLE_VIEWER` 이상)

- `POST /api/v1/config-history/search`: 기간, 대상 종류, 대상 ID, 행위자로 검색하고 페이지로 나눈다.
- 화면에서는 대상 상세 화면에 "변경 이력" 탭으로 붙이는 것이 가장 쓸모 있다.

### 5.4 보존

- 기본 보존 기간은 1년이다. 운영 옵션으로 둔다.
- 오래된 이력 정리는 스케줄 Job으로 한다. 정리 자체도 `SYSTEM` 행위자로 한 줄 남긴다.

## 6. API 키 (타 시스템)

### 6.1 테이블

**`API_CLIENT`**: 호출하는 시스템 하나

| 칼럼 | 설명 |
|---|---|
| CLIENT_ID (PK, String) | 예: `mes-prod`. 감사 기록에 남는 이름 |
| NAME, DESCRIPTION | 설명 |
| ENABLED | false이면 그 클라이언트의 모든 키를 거부한다 |
| ALLOWED_OPCODES | 허용 opcode 목록. `*`는 전체, `LINE1_*`처럼 접두 패턴도 쓴다 |
| ALLOWED_IPS | 허용 IP·CIDR 목록. 비우면 제한 없음(권장하지 않음) |
| ALLOWED_PUSH_URL_PREFIXES | 이 클라이언트가 `pushUrl`, 콜백으로 지정할 수 있는 URL 접두어. 비우면 전역 `tc.delivery.allowed-url-prefixes`를 따른다 |
| RATE_LIMIT_PER_SEC | 초당 요청 한도. 비우면 전역 기본값(§6.8) |
| MAX_CONCURRENT | 동시에 처리 중인 요청 한도(인스턴스당). 비우면 전역 기본값(§6.8) |
| CREATED_*, UPDATED_* | 감사 칼럼 |

**`API_CLIENT_KEY`**: 키 하나. 회전을 위해 클라이언트당 활성 키를 최대 2개까지 둔다.

| 칼럼 | 설명 |
|---|---|
| KEY_ID (PK, String) | 키의 공개 부분. 조회용 |
| CLIENT_ID | 소속 클라이언트 |
| KEY_HASH | 비밀 부분의 SHA-256 |
| STATUS | `ACTIVE`, `REVOKED` |
| EXPIRES_AT | 만료 시각. 발급할 때 정한다(§6.9) |
| CREATED_AT, REVOKED_AT | 발급과 폐기 시각 |
| LAST_USED_AT, LAST_USED_IP | 마지막 사용. 1분에 한 번만 갱신한다 |

### 6.2 키 형식과 검증

- **형식**: `tck_<keyId>_<secret>`
  - keyId: 무작위 12자(base32)
  - secret: 32바이트 무작위값(base64url)
  - 접두어 `tck_`가 있으면 저장소나 로그에 키가 새었을 때 비밀 탐지 도구가 찾기 쉽다.
- **발급할 때 전체 키를 한 번만 보여 준다.** 서버에는 해시만 저장한다.
- **헤더**: `X-API-Key: tck_...`
- **검증 순서**
  1. 형식 확인
  2. KEY_ID로 한 건 조회(전수 비교 없음)
  3. SHA-256 해시를 상수 시간으로 비교
  4. 키 상태와 만료 확인
  5. 클라이언트 ENABLED 확인
  6. 허용 IP 확인
- **해시는 BCrypt가 아니라 SHA-256을 쓴다.** 키는 256비트 무작위값이라 사전 대입이 의미가 없다. 반면 BCrypt는 요청마다 수십 ms를 쓰므로 PLC 요청 경로에 맞지 않는다.
- **캐시**: 검증 결과(KEY_ID → 클라이언트, 상태)를 30초 동안 프로세스 메모리에 둔다.
  - 폐기와 중지는 그 자리에서 캐시를 지운다. 같은 인스턴스에서는 즉시 반영된다.
  - 여러 인스턴스로 운영하면 최대 30초 늦게 반영된다. 즉시 반영이 필요하면 Redis pub/sub으로 무효화한다(그때 추가).

### 6.3 인증 결과와 오류

| 상황 | 응답 |
|---|---|
| 키 없음, 형식 틀림, 없는 키, 해시 불일치, 폐기됨, 만료됨, 클라이언트 중지 | **401 `ERR_401_TC_API_KEY`**. 문구는 모두 같다(어느 경우인지 드러내지 않음). 내부 로그에는 사유를 남긴다 |
| 허용되지 않은 IP | 403 `ERR_403_TC_API_CLIENT_IP` |
| 허용되지 않은 opcode | 403 `ERR_403_TC_OPCODE_FORBIDDEN` |
| 허용되지 않은 pushUrl | 400 `ERR_400` (기존 허용 목록 검사) |

- **인증에 성공하면** `ApiClientAuthentication`을 만든다. principal은 clientId, 권한은 `ROLE_CLIENT`다.
- **401이 반복되면 제한한다.** 같은 IP에서 1분에 30회를 넘으면 5분 동안 429를 낸다(Redis 카운터). 키를 무작위로 대입하는 시도를 막기 위한 것이다.

### 6.4 opcode 권한 검사

- 필터에서는 본문을 읽지 않으므로 opcode를 모른다. 그래서 opcode 검사는 **컨트롤러(`DispatcherController`)가 디스패치 전에 `RequestPrincipals.stampAndAuthorize`로** 한다.
  - (구현하며 바꿈) 처음에는 framework `DispatchInterceptor`에서 하려 했다. 그런데 비동기 요청(`/request`)은 디스패치가 다른 스레드에서 돌아 보안 컨텍스트가 없다. 그래서 요청 스레드에서 판단한다.
  - 같은 자리에서 실제 호출자를 요청(`TcMessageRequest.principal`, 본문으로는 받지 않음)에 찍는다. 감사 인터셉터와 결과 전달 감사는 이 값을 쓴다.
- 인증이 `ApiClientAuthentication`일 때만 검사한다. 사용자(JWT)의 시험 호출과 스케줄 Job의 내부 호출은 이 검사를 받지 않는다. 사용자 호출은 `action.tc.dispatch` 권한으로 따로 막는다.

### 6.5 감사

- **`EXCHANGED_MESSAGE_AUDIT`에 `PRINCIPAL` 칼럼을 추가한다.** 값은 `client:mes-prod`, `user:admin`, `system` 같은 형식이다.
- 요청 본문의 `sender`는 호출자가 마음대로 쓰는 값이다. 실제로 누가 호출했는지는 `PRINCIPAL`로 판단한다.
- 키 발급·폐기와 클라이언트 변경은 설정 변경 이력(§5)에 남긴다. 대상 종류는 `API_CLIENT`, `API_KEY`다. 키 값과 해시는 남기지 않고 KEY_ID만 남긴다.

### 6.6 관리 API (`ROLE_ADMIN` 전용)

| 메서드·경로 | 동작 |
|---|---|
| `GET /api/v1/api-clients` | 목록. 활성 키 수, 가장 가까운 만료일, 마지막 사용 시각 포함 |
| `GET /api/v1/api-clients/{clientId}` | 단건 조회. 키 목록(KEY_ID, 상태, 발급·만료·폐기·마지막 사용 시각) 포함. 해시는 넣지 않는다 |
| `POST /api/v1/api-clients` | 클라이언트 생성 |
| `PUT /api/v1/api-clients/{clientId}` | 허용 opcode, IP, pushUrl, 사용량 한도, 설명 변경 |
| `POST /api/v1/api-clients/{clientId}/enable`, `/disable` | 사용 재개와 중지 |
| `DELETE /api/v1/api-clients/{clientId}` | 삭제. **활성 키가 있으면 409**(먼저 모두 폐기). 키 행도 함께 지운다 |
| `POST /api/v1/api-clients/{clientId}/keys` | 새 키 발급. 본문으로 만료일 수(`expiresInDays`)를 받는다(§6.9). 응답에만 전체 키가 있다. 활성 키가 이미 2개면 409 |
| `POST /api/v1/api-clients/{clientId}/keys/{keyId}/revoke` | 키 폐기 |

**삭제 정책**

- 운영에서는 삭제보다 **중지를 권장한다.**
- 삭제해도 호출 감사(`PRINCIPAL`)와 설정 변경 이력은 clientId 문자열로 남으므로 추적은 끊기지 않는다.
- 다만 같은 clientId를 다시 만들면 옛 기록과 섞인다. 그래서 **삭제한 clientId는 재사용하지 않는다.**
  - (구현하며 바꿈) 설정 변경 이력(3단계)에 기대지 않도록, 삭제는 `API_CLIENT` 행을 지우지 않고 `DELETED_AT`만 남긴다.
  - 그 ID로 생성하면 거부한다(409). 키 행은 지운다.

**무중단 키 회전 절차**

1. 새 키를 발급한다. 이때 활성 키가 2개가 된다.
2. 호출하는 시스템의 설정을 새 키로 바꾼다.
3. 옛 키의 `LAST_USED_AT`이 더 이상 갱신되지 않는 것을 확인한다.
4. 옛 키를 폐기한다.

tc와 호출하는 시스템 모두 재기동이 필요 없다(호출하는 시스템이 설정을 런타임에 다시 읽는다면).

### 6.7 보안 체인 구성

tc에 `SecurityFilterChain`을 하나 더 두고, framework 체인보다 먼저 평가되게 한다.

```java
@Bean
@Order(1)
SecurityFilterChain machineApiChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyFilter,
        MachineBearerFilter bearerFilter /* JwTokenProvider 로 검증만 */) throws Exception {
    var mvc = PathPatternRequestMatcher.withDefaults();
    http.securityMatcher(
            mvc.matcher(HttpMethod.POST, "/api/v1/ifsvc/request"),
            mvc.matcher(HttpMethod.POST, "/api/v1/ifsvc/request-and-reply"))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().hasAnyRole("CLIENT", "ADMIN", "OPERATOR"))
        .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(bearerFilter, ApiKeyAuthenticationFilter.class);
    return http.build();
}
```

**확인할 점: framework `JwtFilter`의 이중 등록**

- framework는 `JwtFilter`를 `@Bean`으로 등록한다. Spring Boot는 `Filter` 빈을 **서블릿 필터로도 자동 등록**한다.
- 지금은 보안 체인 안의 `JwtFilter`가 먼저 실행되고, `OncePerRequestFilter` 표시 때문에 서블릿 단계의 두 번째 실행은 건너뛴다.
- 그런데 `machineApiChain`에는 `JwtFilter`가 없다. 그래서 서블릿 단계의 `JwtFilter`가 실행되고, Bearer 헤더가 없는 API 키 요청에 **401 `EXPIRED_TOKEN`을 낸다.**
- 대처: tc에서 `FilterRegistrationBean<JwtFilter>`를 `setEnabled(false)`로 등록해 서블릿 자동 등록을 끈다. framework 체인 안의 동작은 그대로다.
- **확인함(2026-10-30)**: 대처 빈을 뺀 상태에서 올바른 API 키만으로 호출하면 401 `EXPIRED_TOKEN`이 났다. 빈을 넣으면 200이다. framework에서 고칠 후보로 둔다(`JwtFilter` 빈 등록 방식).
- 필터(`ApiKeyAuthenticationFilter`, `MachineBearerFilter`, `ApiClientQuotaFilter`)는 같은 이유로 **빈으로 등록하지 않고** 체인 설정 안에서 만든다.

### 6.8 사용량 제한(quota)

인증 실패 제한(§6.3)과 별개로, **정상 호출의 양**도 제한한다. 한 클라이언트가 폭주하면 PLC 연결과 dispatch 스레드를 함께 쓰는 다른 클라이언트와 스케줄 Job까지 느려지기 때문이다.

| 한도 | 방식 | 넘었을 때 |
|---|---|---|
| 초당 요청 수(`RATE_LIMIT_PER_SEC`) | Redis 고정 창(`tc:quota:{clientId}:{epochSec}` INCR + 2초 EXPIRE). 인스턴스가 여럿이어도 합산된다 | 429 `ERR_429_TC_RATE_LIMIT`, `Retry-After: 1` |
| 동시 처리 수(`MAX_CONCURRENT`) | 인스턴스마다 클라이언트별 세마포어. `request-and-reply`는 PLC 응답을 기다리며 스레드를 잡고 있으므로 이 한도가 실제 보호 수단이다 | 429 `ERR_429_TC_CONCURRENCY` |

- **전역 기본값(제안)**: 초당 50건, 동시 20건(`tc.api-client.default-rate-limit-per-sec`, `default-max-concurrent`). 클라이언트 칼럼이 비어 있을 때만 쓴다.
- **런타임 변경**: 클라이언트별 값은 테이블에 있으므로 재기동 없이 바뀐다. 캐시(§6.2)를 무효화하면 즉시 반영된다.
- **검사 위치**: 인증 직후, `machineApiChain` 안의 `ApiClientQuotaFilter`. JWT 사용자(화면의 시험 호출)와 스케줄 Job에는 적용하지 않는다.
- **Redis 장애 시**: 초당 한도 검사는 **통과시키고**(fail-open) WARN 로그를 남긴다. 제한 기능 때문에 PLC 요청 전체가 멈추는 것이 더 큰 장애이기 때문이다. 동시 처리 한도는 메모리에서 하므로 Redis와 무관하게 동작한다.
- **기록**: 429는 호출 감사(`EXCHANGED_MESSAGE_AUDIT`)에 남기지 않는다(본문을 읽기 전에 거절하므로). 대신 클라이언트별 1분 집계를 WARN 로그로 남긴다.

### 6.9 키 만료 정책

키가 만료되면 그 시스템의 PLC 요청이 끊긴다. tc는 재기동조차 조심해야 하는 시스템이므로, **만료는 보안 수단이면서 동시에 장애 원인**이 될 수 있다. 그래서 만료를 두되, 놓칠 수 없게 알린다.

- **기본 수명**: 365일. 발급할 때 `expiresInDays`로 바꿀 수 있고, 최대 730일이다.
- **무기한 키는 두지 않는다**(결정 D9에서 다시 판단).
- **만료 임박 알림**
  - 매일 1회 스케줄 Job(`ApiKeyExpiryCheckJob`)이 30일, 7일, 1일 안에 만료되는 활성 키를 찾는다.
  - 찾은 키는 WARN 로그로 남기고, 화면 상단 알림(SSE 이벤트 `apikey.expiring`)으로 띄운다.
  - (선택) 관리자용 WebHook을 지정하면 그곳으로도 보낸다.
- **화면 표시**: 클라이언트 목록에서 가장 가까운 만료일이 30일 안이면 주황색, 7일 안이면 빨간색으로 표시한다.
- **만료 후 처리**: 상태는 `ACTIVE`로 두고, 검증할 때 만료 시각으로 거부한다(401). 만료된 키는 활성 키 2개 한도에 세지 않는다.
- **오래 쓰지 않은 키**: `LAST_USED_AT`이 90일 넘게 갱신되지 않은 활성 키는 목록에 "미사용"으로 표시한다. 폐기 후보다. 자동으로 폐기하지는 않는다.

### 6.10 기존 호출자 전환 계획

현재 PLC 요청 경로는 framework 체인의 `/api/**` 인증, 즉 **JWT만 받는다.** 지금 tc를 부르는 시스템이 있다면, 그 시스템은 사용자 계정으로 로그인해 받은 JWT로 호출하고 있다. 이 시스템들을 끊지 않고 API 키로 옮기는 절차는 다음과 같다.

1. **배포(전환 시작)**
   - `machineApiChain`이 API 키와 JWT를 **둘 다** 받는다(§6.7의 `MachineBearerFilter`).
   - 전환 기간에는 JWT 쪽 허용 역할에 `ROLE_CLIENT`를 남겨 둔다. 기존 시스템 계정의 역할이 이것이기 때문이다.
   - 이 배포 자체가 기존 호출을 끊지 않는지는 §6.12의 "기존 JWT 호출 유지" 시험으로 확인한다.
2. **현황 파악**
   - 배포 뒤 며칠 동안 호출 감사의 `PRINCIPAL`을 집계해, JWT(`user:…`)로 PLC 요청을 보내는 계정 목록을 만든다.
   - 이 목록이 전환 대상이다.
3. **시스템별 교체**
   - 대상 시스템마다 `API_CLIENT`를 만들고, 허용 opcode는 2단계에서 실제로 쓴 opcode로 정한다.
   - 키를 발급해 그 시스템 담당자에게 전달하고, 그쪽 설정을 바꾼다.
   - 교체한 뒤 `PRINCIPAL`이 `client:…`로 바뀐 것을 확인한다.
4. **옛 계정 중지**
   - 해당 시스템의 JWT 호출이 일정 기간(예: 7일) 없으면 그 사용자 계정을 **중지**한다.
   - 런타임 조작이므로 tc 재기동이 필요 없다.
   - 문제가 생기면 계정을 다시 켜는 것으로 바로 되돌릴 수 있다.
5. **정리(다음 계획된 배포)**
   - JWT 쪽 허용 역할에서 `ROLE_CLIENT`를 빼고, 사용자 역할 목록에서도 없앤다.
   - 이 정리는 코드 변경이므로 **따로 재기동하지 않고**, 다음에 예정된 배포에 함께 싣는다.

전환 상태(대상 계정, 클라이언트 생성, 교체 확인, 계정 중지)는 운영 문서에 표로 관리한다.

### 6.11 화면

> 상태: 백엔드 API만 구현했다. 화면은 프론트(레거시 tc-frontend, 이 저장소 밖)에서 따로 만든다.

메뉴: **시스템 관리 → API 클라이언트**. 조회와 편집 모두 `ROLE_ADMIN` 전용이다.

- **목록**
  - 칼럼: clientId, 이름, 사용 여부, 활성 키 수, 가장 가까운 만료일(§6.9 색 표시), 마지막 사용 시각, "미사용" 표시
  - 필터: 사용 여부, 만료 임박
- **상세**
  - 기본 정보 탭: 이름, 설명, 사용 여부
  - 허용 범위 탭: opcode 목록(인터페이스 이름에서 고르거나 패턴 입력), IP·CIDR 목록, pushUrl 접두어, 사용량 한도
  - 키 탭: 키 목록(KEY_ID, 상태, 발급·만료·폐기·마지막 사용 시각과 IP). 버튼은 [새 키 발급], [폐기]
  - 변경 이력 탭: 이 클라이언트와 그 키에 대한 `CONFIG_CHANGE_HIST`
- **키 발급 대화상자**
  - 만료일 수를 입력한다(기본 365).
  - 발급하면 전체 키를 **한 번만** 보여 주고 [복사] 버튼을 둔다.
  - "이 창을 닫으면 키를 다시 볼 수 없습니다" 문구를 표시한다.
  - "안전한 곳에 저장했습니다"에 체크해야 닫을 수 있다.
  - 키는 화면 상태에만 두고, 브라우저 저장소에는 넣지 않는다.
- **폐기 확인**
  - 그 키의 마지막 사용 시각을 보여 준다.
  - 최근 10분 안에 사용됐다면 "지금 쓰이고 있는 키입니다"를 강하게 경고한다. 회전 중에 옛 키를 너무 일찍 폐기하는 실수를 막기 위해서다.

### 6.12 시험 항목

통합 테스트로 작성한다(실제 DB와 Redis를 쓰고, Mock은 쓰지 않는 프로젝트 정책).

| 구분 | 시험 |
|---|---|
| 인증 성공 | 유효한 키로 `request`, `request-and-reply` 호출 시 200·202. 감사 `PRINCIPAL` = `client:{id}` |
| 인증 실패 | 키 없음, 형식 틀림, 없는 KEY_ID, 해시 불일치, 폐기된 키, 만료된 키, 중지된 클라이언트 → 모두 401이고 **본문이 같다** |
| 경로 범위 | 유효한 키로 관리 API(`/api/v1/ifsvc/management/...`, `/api/v1/ifsvc/audit/...`, `/api/v1/plc-connector/...`) 호출 → 401(API 키는 이 경로에서 인증 수단이 아님) |
| 권한 | 허용 밖 opcode → 403. 허용 밖 IP → 403. 허용 밖 pushUrl → 400 |
| 회전 | 키 두 개 모두 유효, 세 번째 발급 → 409, 옛 키 폐기 → 옛 키 401·새 키 200 |
| 캐시 | 폐기·중지 직후 같은 인스턴스에서 바로 401(캐시 무효화) |
| 사용량 제한 | 한도 초과 → 429와 `Retry-After`. 동시 처리 초과 → 429. Redis를 멈췄을 때 초당 한도는 통과하고 WARN 로그 |
| 대입 제한 | 같은 IP에서 401 30회 초과 → 429 |
| 기존 JWT 호출 유지 | 전환 기간 설정에서 `ROLE_CLIENT` 사용자 JWT로 PLC 요청 → 200. 관리 API는 JWT로 그대로 동작 |
| `JwtFilter` 이중 등록 | Bearer 헤더 없이 API 키만으로 호출 → `EXPIRED_TOKEN` 401이 나지 않음 |
| 관리 API | 생성, 수정, 중지, 단건 조회(해시 미포함), 활성 키가 있을 때 삭제 → 409, 삭제한 clientId 재생성 → 409 |
| 이력 | 클라이언트·키 변경마다 `CONFIG_CHANGE_HIST` 1건. JSON에 해시와 키 값이 없음 |
| 만료 알림 | 29일, 6일 뒤 만료 키를 만들고 `ApiKeyExpiryCheckJob` 실행 → 알림 대상에 포함 |

## 7. 재기동과 운영 옵션

| 항목 | 런타임 변경 | 비고 |
|---|---|---|
| 사용자 생성·중지·역할·비밀번호 | 됨 | §3.3 |
| API 클라이언트·키 발급·폐기·허용 범위 | 됨 | §6.6. 캐시 때문에 최대 30초(여러 인스턴스일 때) |
| 클라이언트별 사용량 한도 | 됨 | §6.8. 테이블 칼럼. 전역 기본값은 설정값이라 재기동 필요 |
| 기존 호출자 전환(옛 계정 중지·복구) | 됨 | §6.10. `ROLE_CLIENT` 정리만 다음 계획된 배포에 싣는다 |
| 권한 검사 모드(AUDIT, ENFORCE) | 됨 | 운영 옵션 |
| 로그인·API 키 실패 제한 값, 이력 보존 기간 | 됨(운영 옵션으로 둘 때) | 처음에는 설정값, 필요하면 옵션 테이블로 |
| **JWT 서명 키(`TC_JWT_SECRET`)** | **재기동 필요** | framework가 기동할 때 읽는다. 바꾸면 사용자 토큰이 모두 무효가 된다. API 키 호출은 영향 없음 |
| **access·refresh 토큰 수명** | **재기동 필요** | 같은 이유 |
| **DB·Redis 접속 정보** | **재기동 필요** | |
| bootstrap 관리자 설정 | 해당 없음 | 테이블이 비어 있을 때만 쓴다 |

- **토큰 수명 조정을 제안한다.** 현재 access는 약 2.8시간(10,000,000ms), refresh는 약 11.6일이다.
  - 중지된 사용자도 access 토큰이 만료될 때까지는 쓸 수 있다(JWT는 서명만 확인하기 때문).
  - access를 **30분**으로 줄이기를 권한다. refresh로 계속 재발급되므로 사용자 불편은 없다.
  - 다음 재기동 때 함께 반영한다.

## 8. 비상 복구

활성 관리자가 아무도 로그인할 수 없게 되면 재기동 없이 DB에서 복구한다.

```sql
-- 해시는 별도 도구로 만든다(예: htpasswd -bnBC 10 "" '<새 비밀번호>' | tr -d ':\n' | sed 's/$2y/$2a/')
UPDATE USER SET PASSWORD = '<bcrypt 해시>', ENABLED = 1, PASSWORD_MUST_CHANGE = 1 WHERE ID = '<관리자 ID>';
```

- 로그인 실패 제한에 걸렸다면 Redis에서 `tc:login-fail:*` 키를 지운다.
- 이 조작은 DB 감사 로그 외에는 tc 이력에 남지 않는다. 복구한 뒤 tc에 로그인해 `CONFIG_CHANGE_HIST`에 사유를 남기는 절차(예: 본인 비밀번호 변경)를 운영 문서에 둔다.

## 9. 구현 순서

> 진행 상황(2026-10-30): **1~7단계 구현 완료**(§9.1~§9.3). 7단계는 코드 정리를 마쳤고, 실제 호출 시스템의 전환은 운영 절차(docs/operations.md §6.2)로 진행한다.

1. **보안 체인 정리**: `JwtFilter` 서블릿 자동 등록 끄기, `machineApiChain`(API 키와 JWT 둘 다 받음, 전환 기간에는 `ROLE_CLIENT` 허용), `ApiKeyAuthenticationFilter`. 기존 JWT 호출이 그대로 되는지 확인한다.
2. **API 클라이언트·키**: 테이블, 서비스, 관리 API(단건 조회, 삭제 정책 포함), 캐시, `ClientOpcodeGuard`, 감사 `PRINCIPAL`, 사용량 제한(§6.8), 만료 알림 Job(§6.9), 화면(§6.11), 시험(§6.12).
   - **이 단계를 배포하면 전환 계획(§6.10) 1~4단계를 운영 작업으로 시작한다.** 이후 코드 단계와는 독립적으로 진행된다.
3. **설정 변경 이력**: 테이블, `ConfigChangeRecorder`, 각 관리 서비스에 연결, 조회 API.
4. **사용자 정리**: 칼럼 정리(migration), 가입 흐름 제거, 사용자 관리 API, 본인 API, bootstrap, 로그인 실패 제한, identity 개선점.
5. **권한**: `TcPermissionChecker`, 각 컨트롤러에 `@Requires`(GET 변경 API는 메서드 단위), AUDIT 모드로 운영하며 누락을 채운 뒤 ENFORCE로 전환.
6. **migration SQL**: `migration/002_auth_audit.sql`. 테이블 추가, `USER` 칼럼 정리, `EXCHANGED_MESSAGE_AUDIT.PRINCIPAL` 추가.
7. **전환 정리**: 모든 시스템이 API 키로 옮겨 가면(§6.10 5단계) `ROLE_CLIENT`를 JWT 허용 역할과 사용자 역할 목록에서 뺀다. 다음 계획된 배포에 싣는다.

단계마다 로컬 e2e(tc ↔ vplc)로 PLC 요청 경로가 끊기지 않는지 확인한다.

### 9.1 1·2단계 구현 메모

- **코드 위치**
  - `com.wow.be.tc.apiclient`: 클라이언트·키 정보, 서비스, 설정(`tc.api-client.*`), 만료 알림
  - `com.wow.be.tc.security`: 보안 체인, 필터, 요청 제한, 호출자 판단, 관리자 검사
  - `endpoints/ApiClientManagementController`: 관리 API
- **관리자 검사**: `@Requires`를 켜기 전(5단계)까지는 관리 API가 `TcRoles.requireAdmin()`으로 `ROLE_ADMIN`을 확인한다.
- **설정 변경 이력**: 클라이언트·키 변경은 지금은 INFO 로그로만 남는다. 3단계에서 `CONFIG_CHANGE_HIST`에 연결한다.
- **DB**: `migration/002_api_client.sql`. 표 두 개와 감사 `PRINCIPAL` 칼럼을 추가한다(이 문서의 6단계 중 API 키 부분).
- **로컬 e2e에서 확인한 것**(§6.12 항목 기준)
  - 인증 성공, 인증 실패(형식·없는 키·해시 불일치는 본문까지 같은지, 폐기·만료·중지·인증 없음은 401인지), 경로 범위, opcode·IP·pushUrl 권한
  - 회전(2개 한도, 폐기 즉시 반영), 만료, 중지·재개, 단건 조회에 해시 없음
  - 초당·동시 처리 한도, 대입 차단, 삭제 정책, 만료 임박 알림
  - 감사 `PRINCIPAL`, 기존 JWT 호출과 관리 API 유지, `JwtFilter` 이중 등록 대처
- **확인하지 않은 것**
  - Redis 장애 시 fail-open
  - 여러 인스턴스에서의 캐시 지연
  - 관리자가 아닌 사용자의 403(관리자가 아닌 시험 계정이 없음)
- **대입 차단 집계**: IP별 실패 수에는 폐기·만료·중지 키의 401도 들어간다. 키가 만료된 시스템이 계속 재시도하면 그 IP가 5분 막힐 수 있다. 새 키로 바꾼 뒤에도 차단이 남아 있으면 Redis에서 `tc:apikey-block:{ip}`를 지운다.

### 9.2 3~6단계 구현 메모 (2026-10-30)

**설정 변경 이력(3단계)**
- `com.wow.be.tc.confighist`: 기록기(`ConfigChangeRecorder`), 조회(`POST /api/v1/config-history/search`, `GET /api/v1/config-history/{대상 종류}/{대상 ID}`), 보존 정리(`ConfigChangePurger`, 매일 03:30).
- 연결한 곳: API 클라이언트·키, PLC 커넥터(등록·수정·삭제·게시·해제), 인터페이스(사용 여부 일괄 포함), WebHook, 전달 정의, 스케줄 Job(등록·수정·삭제·일시정지·재개·전체 재적재), 사용자, 권한 검사 모드.
- 운영 상태 필드(게시·트리거 상태)와 수정 시각만 바뀐 UPDATE 는 남기지 않는다. 상태 변화는 CONTROL 로 따로 남는다.
- `migration/004_config_change_hist.sql`.

**권한(5단계)**
- `TcPermissionChecker`(역할 표), `TcPermissionModeSource`(모드는 Redis `tc:option:permission-mode`, 없으면 설정값 AUDIT), `PUT /api/v1/security/permission-mode`(관리자, 이력에 남음).
- 컨트롤러 선언: 클래스에 자원, 상태를 바꾸는 GET(삭제 D·사용 변경 U·제어 X)과 조회용 POST(R)는 메서드에 동작을 명시했다(52곳). `/api/**` 아래 모든 컨트롤러에 선언이 있어 ENFORCE 로 바꿔도 막히는 API 는 없다.
- 레거시 역할 MANAGER·EMPLOYEE 는 각각 OPERATOR·VIEWER 로 본다.
- **전환 기간**: 전환 스위치가 켜져 있는 동안은 로그인한 사용자 누구나 PLC 요청 가능(§6.10). 7단계에서 기본 꺼짐으로 바꿨다(§9.3).
- 관리 API 의 임시 관리자 검사(`TcRoles`)는 `@Requires("tc.apiclient")` 로 바꾸고 지웠다.

**사용자(4단계)**
- 사용자 모듈을 새로 썼다: 가입 요청·승인 제거, `USER` 칼럼 정리, 사용자 관리 API(`/api/v1/users`, 관리자), 본인 API(`/api/v1/me`, `/api/v1/me/password`), 최초 관리자(`UserBootstrap`), 비밀번호 변경 강제(`PasswordChangeRequiredInterceptor`), 로그인 실패 제한(`LoginThrottle`).
- 예전 코드의 결함도 함께 없어졌다: 사용자 `enabled` 를 검사하지 않음(중지해도 로그인됨), `from()` 이 역할을 저장하지 않음, 없는 아이디에 500.
- **로그인 실패 제한을 바꿈**: 아이디별 5회, **IP별 20회**(설계는 같은 값이었다). 같은 사무실 NAT 뒤의 한 사람이 틀린 것 때문에 모두가 막히지 않게 하기 위해서다.
- 사용 중지·삭제·비밀번호 초기화 때 그 사용자의 토큰을 모두 지운다 — framework `JwtFilter` 는 저장된 토큰만 통과시키므로 access 토큰도 바로 끊긴다(§7 의 "access 토큰 수명 동안 유효" 걱정은 해당하지 않는다).
- `migration/005_user_cleanup.sql`(새 칼럼, `enabled` NULL → 1. 옛 칼럼 DROP 은 주석 — 백업 확인 뒤 실행).

**로컬 e2e**(scratchpad 스크립트, 2026-10-30)

| 묶음 | 결과 |
|---|---|
| PLC 어댑터(가짜 PLC) | 10/10 |
| API 키 인증·권한·사용량 | 40/40 |
| 미검증 경로(WRITE·콜백·스케줄·해제) | 17/17 |
| 설정 변경 이력 | 11/11 |
| 권한·사용자 | 54/54 |

**확인하지 않은 것**: 최초 관리자 생성(빈 DB 가 필요), Redis 장애 시 fail-open, 여러 인스턴스, `ConfigChangePurger` 실제 삭제.

### 9.3 7단계 전환 정리 (2026-10-30)

- **전환 스위치를 런타임 옵션으로 바꿨다**: `DispatchTransitionOption`(Redis `tc:option:dispatch-any-user`, 없으면 설정값). 관리 API `GET/PUT /api/v1/security/dispatch-any-user`(관리자, 변경 이력에 남음). 인스턴스마다 5초 캐시.
  - 처음 설계는 "다음 계획된 배포에 설정값을 false 로"였다. 그런데 실제 호출 시스템이 모두 옮겼는지는 운영 환경의 감사 기록으로만 알 수 있고, 잘못 판단하면 재기동해야 되돌릴 수 있었다. 그래서 **기본은 꺼짐(전환 끝)으로 두되 재기동 없이 즉시 켤 수 있게** 했다.
- **PLC 요청 권한(CLIENT 표)은 API 키 주체에만** 준다. 사용자 역할의 `ROLE_CLIENT` 는 더 이상 권한이 없다.
- **사용자에게 줄 수 있는 역할은 ADMIN·OPERATOR·VIEWER 셋뿐**이다(`ROLE_CLIENT`, 레거시 MANAGER·EMPLOYEE 는 새로 줄 수 없음, 400).
- 로컬 확인: 지난 7일 JWT 로 PLC 요청을 보낸 계정은 시험 계정뿐, `ROLE_CLIENT` 사용자 없음. e2e `test_users.py` 에 스위치 꺼짐 403 → 켬 200 → 되돌림 403, `ROLE_CLIENT` 부여 400 을 더했다(60/60).
- **운영 반영 때**: 운영 감사 기록으로 전환 대상이 남았는지 먼저 본다(operations.md §6.2). 남았다면 배포 직후 스위치를 켠다.

## 10. 결정할 것

| # | 질문 | 제안 |
|---|---|---|
| D1 | 역할을 셋(ADMIN, OPERATOR, VIEWER)으로 할지, ADMIN 하나로 시작할지 | 구조는 셋으로 만들고 처음에는 ADMIN만 써도 된다 |
| D2 | API 키 헤더 이름 | **확정(2026-10-30)**: `X-API-Key` |
| D3 | 요청 본문의 `sender`를 인증된 clientId로 덮어쓸지 | 덮어쓰지 않는다. `PRINCIPAL`을 따로 남긴다 |
| D4 | 설정 변경 이력 보존 기간 | 1년 |
| D5 | 상태를 바꾸는 GET API를 POST·DELETE로 바꿀지 | **완료(2026-10-31)**: 삭제는 DELETE, 제어·사용 변경·연결 시험은 POST. 경로는 그대로, 예전 GET 은 405 |
| D6 | access 토큰 수명 | 30분 |
| D7 | 여러 인스턴스 운영(무중단 재기동) 검토 | 별도 운영 설계. PLC 동시 접속 가능 여부와 변경 감지 상태 공유를 확인해야 한다 |
| D8 | 사용량 한도 전역 기본값 | **확정(2026-10-30)**: 초당 50건, 동시 20건. 실제 호출량을 전환 2단계(§6.10)에서 보고 조정 |
| D9 | 키 만료: 강제 만료(기본 365일, 최대 730일)와 무기한 키(나이 경고만) 중 무엇으로 할지 | **확정(2026-10-30)**: 강제 만료 + 30·7·1일 전 알림 |
| D10 | 전환 기간에 옛 계정의 JWT 호출이 없어야 하는 기간 | 7일 |
