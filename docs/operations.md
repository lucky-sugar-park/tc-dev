# tc 운영 가이드

> 기준: 2026-10-30, 브랜치 v0.1.0. 단일 인스턴스 운영 기준이다. 다중화는 [plc-ha-design.md](plc-ha-design.md)(설계만 있음).
> tc 를 재기동하면 tc 를 부르는 MES 등의 PLC 요청이 그동안 끊긴다. **런타임에 바꿀 수 있는 것은 재기동하지 않는다**(§2).

## 1. 구성

| 구성 요소 | 기본 포트 | 메모 |
|---|---|---|
| tc | 8087 (`TC_SERVER_PORT`) | PLC 게이트웨이. 관리 화면(SPA)과 API |
| vplc | 8088 | 시험용 가상 PLC. 운영에는 쓰지 않는다 |
| MariaDB | 3306 | DB `tc`. 설정·이력·Quartz(JDBC 저장, 클러스터) |
| Redis | 6379 | 사용자 토큰, PLC 응답 변경 감지, 요청 제한 집계, 권한 검사 모드, PLC 연결 상태(`tc:plc-state:*`, 만료 15초), 다중화를 켜면 PLC 임대(`tc:plc-owner:*`)·인스턴스 목록(`tc:instance:*`). 키 접두어 `tc:` |
| PLC | 커넥터마다 | MELSEC MC 3E 바이너리. 포트당 연결 1개 |

**tc 를 두 대 이상 띄울 때**: 모든 tc 서버의 시계를 NTP 로 맞춘다 — Quartz 클러스터가 시각으로 실행을 나누고 죽은 인스턴스를 판단한다. 스케줄 Job 은 클러스터 전체에서 한 번에 한 실행이고, PLC 수집·정리 Job 은 앞 실행이 끝나기 전에 다음 실행이 겹치지 않는다(docs/plc-ha-design.md §9.2).

**Redis 가 없을 때**: API 키로 오는 PLC 요청은 계속 처리된다(제한 기능은 통과시키고 경고를 남김). 사용자 로그인·토큰 재발급은 되지 않는다. `/api/health` 는 `DEGRADED`(200)를 돌려준다.

## 2. 설정

### 2.1 환경 변수

| 변수 | 필수 | 내용 |
|---|---|---|
| `TC_DB_PASSWORD` | ✔ | DB 비밀번호 |
| `TC_JWT_SECRET` | ✔ | JWT 서명 키(Base64, 48바이트 이상 권장) |
| `TC_DB_URL`, `TC_DB_USERNAME` | | 기본 `jdbc:mariadb://localhost:3306/tc`, `root` |
| `TC_REDIS_HOST`, `TC_REDIS_PORT` | | 기본 localhost:6379 |
| `TC_TRUSTED_PROXIES` | | 리버스 프록시 주소(쉼표). 이 주소에서 온 요청만 `X-Forwarded-For` 를 믿는다 |
| `TC_CORS_ALLOWED_ORIGIN_PATTERNS` | | 화면 출처 |
| `TC_BOOTSTRAP_ADMIN_ID`, `TC_BOOTSTRAP_ADMIN_PASSWORD_HASH` | 처음만 | 최초 관리자(§3) |
| `TC_PLC_OWNERSHIP_ENABLED` | 다중화 때 | `true` 면 PLC 마다 연결할 인스턴스를 Redis 임대로 정한다(기본 false — 한 대 운영). [plc-ha-design.md](plc-ha-design.md) §10 |
| `TC_INTERNAL_SECRET` | 다중화 때 | 인스턴스 사이 PLC 교환 서명 키 — 모든 인스턴스가 같은 값. 바꿀 때는 옛 값을 `TC_INTERNAL_SECRET_PREVIOUS` 로 함께 주고 차례로 재기동한 뒤 옛 값을 뺀다 |
| `TC_INSTANCE_INTERNALURL`, `TC_INSTANCE_INTERNALPORT` | 다중화 때 | 이 인스턴스의 내부 주소(예: `http://10.0.1.11:8187`)와 포트. 방화벽은 tc 인스턴스끼리만 그 포트를 연다. 포트를 따로 두지 않으면(0) LB 에서 `/internal/**` 를 막는다 |
| `TC_SECRET_KEY` | DB_TABLE 전달을 쓰면 | 외부 DB 연결 비밀번호 암호화 키(Base64 32바이트). **바꾸거나 잃으면 저장된 비밀번호를 다시 입력해야 한다** — DB 백업과 따로 보관. [db-table-delivery.md](db-table-delivery.md) §3.1 |

비밀값은 저장소에 두지 않는다. 로컬 개발은 `application-local.yml`(gitignored, 실행 jar 에서 빠짐)을 쓴다.

### 2.2 재기동 없이 바꾸는 것 / 재기동이 필요한 것

| 재기동 없이(관리 API·화면) | 재기동 필요(설정 파일·환경 변수) |
|---|---|
| 사용자, API 클라이언트·키, 허용 범위·사용량 한도 | `TC_JWT_SECRET`(바꾸면 모든 사용자 토큰 무효. API 키 호출은 영향 없음) |
| PLC 커넥터·인터페이스·WebHook·전달 정의·스케줄 Job. WebHook 은 다른 인스턴스에서 바꿔도 10초 안에 따라온다 | access·refresh 토큰 수명(`jwt.*`) |
| 결과 기록용 DB 연결(`/api/v1/db-connections`) | `TC_SECRET_KEY`, `tc.db-connection.*` |
| 커넥터 게시·해제 | DB·Redis 접속 정보 |
| 권한 검사 모드(`PUT /api/v1/security/permission-mode`) | `tc.api-client.*` 기본값, `tc.security.login-throttle.*`, `tc.delivery.*`, `plc.*`, `tc.plc.ownership.*`(다중화 소유권 — 기본 꺼짐, 켜면 PLC 연결이 Redis 임대에 달린다) |
| 기존 JWT 호출자 전환 스위치(`PUT /api/v1/security/dispatch-any-user`, §6.2) | |

## 3. 설치와 업그레이드

### 3.1 DB 스크립트 (`wow-tc-backend/migration/`)

번호 순서대로 한 번씩 돌린다. 모두 다시 돌려도 안전하게(IF NOT EXISTS 등) 썼다. 앱을 멈추지 않아도 된다.

| 파일 | 내용 |
|---|---|
| `001_long_text_longtext.sql` | 긴 글 칼럼을 LONGTEXT 로(예전 TINYTEXT·VARCHAR(255)) |
| `002_api_client.sql` | API 클라이언트·키 표, 감사 `PRINCIPAL` 칼럼 |
| `003_schedule_hist_remark_longtext.sql` | 스케줄 실행 이력 `REMARK` LONGTEXT(성공 이력 저장 실패 해결) |
| `004_config_change_hist.sql` | 설정 변경 이력 표 |
| `005_user_cleanup.sql` | 사용자 새 칼럼, `enabled` NULL → 1. **옛 칼럼 DROP 은 주석 — 백업 확인 뒤 따로 실행** |
| `006_db_table_delivery.sql` | 결과 기록용 DB 연결 표, 전달 정의 `TABLE_NAME` 칼럼 |
| `007_plc_async_columns.sql` | 지운 비동기 큐 설정 칼럼(`plc_connector.async_*`)을 NULL 허용으로. **DROP 은 주석 — 백업 확인 뒤 따로 실행** |

```bash
mysql -u root -p tc < migration/002_api_client.sql
```

`ddl-auto=update` 로 띄우면 새 표·칼럼은 앱이 만들지만, 칼럼 형을 넓히거나(001, 003) 값을 고치는 일(005)은 하지 않는다. 스크립트를 꼭 돌린다.

### 3.2 최초 관리자

`USER` 표가 비어 있을 때만 쓴다. 한 명이라도 있으면 설정을 읽지 않는다.

1. 비밀번호 해시를 만든다: `wow-tc-backend/tools/BcryptHash.java`(비밀번호를 표준 입력으로 받음).
2. `TC_BOOTSTRAP_ADMIN_ID`, `TC_BOOTSTRAP_ADMIN_PASSWORD_HASH` 를 주고 기동한다. 로그에 `최초 관리자를 만들었습니다` 가 남는다.
3. 그 관리자로 로그인하면 비밀번호를 바꾸기 전까지 다른 API 가 403(`ERR_403_TC_PASSWORD_CHANGE_REQUIRED`)이다. `PUT /api/v1/me/password` 로 바꾼다.
4. 환경 변수에서 해시를 지운다(다음 기동부터 읽지 않지만 남겨 둘 이유가 없다).

## 4. 재기동

### 4.1 한 대 운영

재기동하는 동안 PLC 요청이 끊기므로 호출하는 시스템에 미리 알린다.

1. **정상 종료 신호를 보낸다.** Linux 는 `SIGTERM`(`systemctl stop`, `kill <pid>`), Windows 서비스는 서비스 중지. 강제 종료(`kill -9`, 작업 끝내기)는 쓰지 않는다.
   - tc 는 ① Quartz 를 멈추고 ② 새 요청을 받지 않고 진행 중 요청을 마친 뒤(`server.shutdown=graceful`, 단계별 최대 25초) ③ PLC 연결을 닫는다. 연결을 먼저 닫아야 PLC 슬롯이 빨리 비어 다시 뜬 tc 가 바로 연결한다.
   - 커넥터의 게시 상태는 그대로 남아 다음 기동 때 다시 연결된다.
2. 기동한다. PLC 가 꺼져 있어도 tc 는 뜨고 뒤에서 계속 연결한다(예전에는 앱이 종료됐다).
3. 확인한다.
   - `GET /api/health` → `{"status":"UP"}`
   - 게시된 커넥터의 `connection` 에 채널이 있는지(`GET /api/v1/plc-connector/connection-states`, `activeChannels` ≥ 1)
   - PLC 요청 한 건(예: 읽기 인터페이스)

**PLC 가 새 연결을 거부할 때**: 이전 tc 가 강제 종료되어 PLC 가 옛 연결을 아직 잡고 있는 경우다. PLC 의 생존 확인 시간이 지나면 tc 가 스스로 다시 연결한다(§5). 급하면 PLC 쪽 연결을 끊는다.

### 4.2 다중화 운영 (두 대 이상)

설계와 동작은 [plc-ha-design.md](plc-ha-design.md). 요약하면 API 는 어느 인스턴스로 와도 처리하고, PLC 연결은 PLC 마다 한 인스턴스(주인)만 가진다. 주인이 아닌 인스턴스로 온 PLC 요청은 주인에게 PLC 교환만 넘긴다. 주인이 사라지면 다른 인스턴스가 이어받는다.

| 상황 | 요청이 끊기는 시간(로컬 시험) |
|---|---|
| 주인을 정상 종료(순차 재기동) | 1초 안팎(0.4~0.8초) — 주인이 아닌 인스턴스의 재기동은 끊김 없음 |
| 주인이 갑자기 죽음 | 9초 안팎(임대 10초 만료 + 검사 2초) + PLC 가 죽은 연결을 정리하는 시간(§5 생존 확인) |

로컬 두 대로 확인한 것(2026-10-31): 순차 재기동(주인 아닌 쪽 실패 0건, 주인 쪽 0.8초), PLC 하나 옮기기, 강제 종료 이어받기(9초). 확인하지 못한 것: Redis 전체 장애·한 인스턴스만 Redis 차단(실제 다중 서버에서).

#### 4.2.1 준비 (모든 인스턴스)

| 항목 | 값 |
|---|---|
| 같아야 하는 것 | DB, Redis, `TC_JWT_SECRET`(사용자 토큰이 어느 인스턴스에서나 통하게), `TC_INTERNAL_SECRET`, `TC_SECRET_KEY`, `plc.*` 설정 |
| 인스턴스마다 다른 것 | `TC_INSTANCE_INTERNALURL`(예: `http://10.0.1.11:8187`), `TC_INSTANCE_INTERNALPORT`(예: 8187) |
| 켜는 것 | `TC_PLC_OWNERSHIP_ENABLED=true` |
| 시계 | NTP 로 맞춘다 — 스케줄(Quartz 클러스터)과 내부 요청 서명(±30초)이 시각을 쓴다 |
| 방화벽 | 내부 포트는 tc 인스턴스끼리만. 내부 포트를 따로 두지 않으면(0) LB 에서 `/internal/**` 를 막는다 |
| LB | 헬스 체크 `GET /api/health`. 재기동할 인스턴스를 먼저 빼는 기능(드레인)이 있어야 한다 |
| Redis | MES 와 **따로 둔다**(별도 인스턴스). tc 의 Redis 설정은 호스트·포트만 받아 DB 번호로 나눌 수 없다. MES 쪽 `FLUSHDB` 로 임대가 지워져도 주인이 바로 다시 잡지만 사용자 토큰·사용량 집계도 함께 사라진다 |
| PLC | 포트마다 연결을 **하나만** 받게 둔다 — Redis 에서 끊긴 옛 주인과 새 주인이 같은 PLC 에 함께 붙는 것을 PLC 가 막는다. 생존 확인은 10~30초(§5) |

**주의 — 켜면 PLC 연결이 Redis 에 달린다.** 기동할 때 Redis 가 없으면 그 인스턴스는 아무 PLC 에도 연결하지 않는다(이미 가진 연결은 Redis 가 죽어도 유지한다). 한 대로 운영하는 동안에는 켜지 않는다.

#### 4.2.2 한 대 → 두 대 전환

계획된 재기동 한 번이 든다.

1. 새 서버에 tc 를 설치하고 §4.2.1 대로 설정한다. 아직 띄우지 않는다.
2. 기존 인스턴스에 `TC_PLC_OWNERSHIP_ENABLED=true`, `TC_INTERNAL_SECRET`, 내부 주소·포트를 넣고 §4.1 대로 재기동한다. 기동 직후 게시된 PLC 의 임대를 모두 잡고 연결한다(로그 `임대를 얻었습니다`).
3. 새 인스턴스를 띄운다. PLC 는 이미 주인이 있으므로 연결하지 않고 원격 어댑터만 둔다(로그 `요청을 그쪽으로 넘깁니다`).
4. 확인(§4.2.5) 뒤 LB 에 새 인스턴스를 넣는다.

PLC 연결은 처음 인스턴스에 몰려 있다. 처리에는 문제가 없고, 그 인스턴스가 죽으면 모든 PLC 가 한꺼번에 넘어간다는 것만 다르다(결정 H4). 나누고 싶으면 §4.2.4.

#### 4.2.3 순차 재기동 (배포·설정 변경)

한 번에 한 대씩.

1. LB 에서 그 인스턴스를 뺀다(드레인). 새 요청이 다른 인스턴스로 간다.
2. 정상 종료한다(§4.1 1번과 같음, 강제 종료 금지). tc 는 진행 중 요청을 마치고 → PLC 연결을 닫고 → 임대를 반납한다. 다른 인스턴스가 2초 안에 그 PLC 들을 이어받는다(로그 `임대를 얻었습니다`).
3. 기동한다. 이어받은 PLC 는 다른 인스턴스에 그대로 있고, 이 인스턴스는 원격 어댑터로 넘긴다.
4. 확인(§4.2.5) 뒤 LB 에 다시 넣는다. 다음 인스턴스로.

마지막에 재기동한 인스턴스는 PLC 를 하나도 갖지 않는다 — 정상이다.

#### 4.2.4 PLC 를 다른 인스턴스로 옮기기

옮기는 API(handover)는 아직 없다. 필요하면 둘 중 하나로 한다.

- **그 PLC 를 가진 인스턴스를 순차 재기동한다**(§4.2.3) — 그 인스턴스의 PLC 가 모두 다른 인스턴스로 옮겨간다. 끊김 1초 안팎.
- **PLC 하나만 옮기기**: 해제(`POST /api/v1/plc-connector/control/release/byName/{name}`) → 옮길 인스턴스에 직접 게시(`…/control/publish/byName/{name}`, LB 를 거치지 않고 그 인스턴스 주소로). 해제부터 게시까지, 그리고 게시 뒤 다른 인스턴스가 새 주인을 알아채기까지(검사 주기 2초) 그 PLC 요청은 실패할 수 있다.

#### 4.2.5 확인

- 각 인스턴스 `GET /api/health` → `UP`
- `GET /api/v1/plc-connector/connection-states` — 게시된 커넥터마다 `instance`(주인)와 `activeChannels` ≥ 1. 어느 인스턴스에 물어도 같다
- 주인이 아닌 인스턴스로 PLC 요청 한 건 → 200
- 로그에 `서명이 맞지 않는 내부 PLC 교환 요청`, `내부 요청 서명을 받지 않습니다`(→ `TC_INTERNAL_SECRET` 이 다르다), `internal-url 이 없어`(→ 내부 주소 설정 누락)가 없는지

#### 4.2.6 장애 때

| 증상 | 원인·대응 |
|---|---|
| 인스턴스 하나가 죽음 | 10초 안팎에 다른 인스턴스가 이어받는다. PLC 가 죽은 연결을 오래 잡으면 그만큼 늦다(생존 확인). 죽은 인스턴스를 고쳐 띄우면 원격 어댑터로 들어온다 |
| 어느 PLC 요청만 계속 503 `PLC 를 연결한 인스턴스가 바뀌는 중` | 주인이 막 바뀌는 중 — 몇 초 뒤 다시. 계속되면 `connection-states` 에서 그 PLC 의 `instance` 를 보고 그 인스턴스의 로그·내부 포트 방화벽을 본다 |
| `PLC 를 연결한 인스턴스에 닿지 않습니다` | 주인의 내부 포트에 닿지 않는다(방화벽·내부 주소 오타). 주인이 죽었으면 임대 만료 뒤 이어받는다 |
| Redis 가 죽음 | 모든 인스턴스가 지금 연결을 유지하고 PLC 요청도 계속된다. 새로 뜨는 인스턴스는 PLC 에 연결하지 못한다 — Redis 를 살리기 전에는 재기동하지 않는다. 사용자 로그인은 되지 않는다(§1) |
| 한 인스턴스만 Redis 에서 끊김 | 그 인스턴스는 연결을 유지하고, 10초 뒤 다른 인스턴스가 임대를 잡아 연결을 시도하지만 PLC(포트당 연결 하나)가 거부한다. 끊긴 인스턴스가 Redis 에 다시 닿으면 남의 임대를 보고 연결을 닫고 다른 인스턴스가 연결한다. 오래가면 그 인스턴스를 LB 에서 빼고 정상 종료한다 |

#### 4.2.7 비밀값 바꾸기 (`TC_INTERNAL_SECRET`)

1. 모든 인스턴스를 차례로(§4.2.3) 새 값 `TC_INTERNAL_SECRET` + 옛 값 `TC_INTERNAL_SECRET_PREVIOUS` 로 재기동한다. 보내는 쪽은 새 값으로 서명하고 받는 쪽은 둘 다 받으므로 도중에 끊기지 않는다.
2. 모두 바뀐 뒤 `TC_INTERNAL_SECRET_PREVIOUS` 를 빼고 다시 차례로 재기동한다(급하지 않으면 다음 배포 때).

#### 4.2.8 한 대로 되돌리기

1. 남길 인스턴스 하나를 빼고 나머지를 차례로 정상 종료한다 — 그 PLC 들이 남은 인스턴스로 옮겨간다.
2. 남은 인스턴스에서 `TC_PLC_OWNERSHIP_ENABLED` 를 지우고 재기동한다(§4.1). 기동 때 게시된 커넥터를 모두 직접 연결하고 Redis 없이도 PLC 요청을 처리한다.

## 5. PLC 쪽 설정

- **생존 확인(KeepAlive)을 짧게**(10~30초) 둔다. tc 가 전원·네트워크 문제로 FIN 없이 사라지면 PLC 가 그 연결을 계속 잡아 슬롯을 비우지 않는다.
- **포트 수 = tc 가 쓸 연결 수.** 커넥터의 포트와 로컬 VIP 를 같은 개수로 맞춘다(`min(포트 수, VIP 수)` 만큼 연결한다).
- 시간 초과(`plc.request.timeout-ms`, 기본 20초)가 나면 tc 는 그 연결을 닫고 다시 연결한다. 늦은 응답이 다음 요청에 섞이지 않게 하기 위해서다. 시간 초과가 자주 나면 PLC·네트워크 상태를 먼저 본다.

## 6. API 키 (타 시스템)

### 6.1 발급·회전·폐기

| 일 | API(관리자) |
|---|---|
| 클라이언트 만들기 | `POST /api/v1/api-clients` — 허용 opcode(`*`, `LINE1_*`), 허용 IP·CIDR, pushUrl 접두어, 사용량 한도 |
| 키 발급 | `POST /api/v1/api-clients/{id}/keys` — **응답의 `apiKey` 는 이번 한 번만 보인다.** 기본 365일, 최대 730일 |
| 회전 | ① 새 키 발급(활성 2개까지) → ② 호출 시스템 설정 교체 → ③ 옛 키의 `lastUsedAt` 이 멈췄는지 확인 → ④ 옛 키 폐기 |
| 폐기 | `POST /api/v1/api-clients/{id}/keys/{keyId}/revoke` — 즉시 401 |
| 중지·삭제 | `POST …/disable`, `DELETE …`(활성 키가 있으면 409, 삭제한 ID 는 재사용 불가) |

호출 시스템은 `X-API-Key: tck_…` 헤더로 `POST /api/v1/ifsvc/request` 또는 `/request-and-reply` 를 부른다.

**만료 임박 알림**: 매일 08:00 에 30·7·1일 안에 만료되는 키를 WARN 로그와 화면 알림으로 알린다. 로그 키워드 `API 키 만료 임박`.

**대입 차단**: 한 IP 에서 1분에 401 이 30번을 넘으면 그 IP 를 5분 막는다(429). 만료된 키로 계속 재시도하는 시스템도 막힐 수 있다. 새 키로 바꾼 뒤에도 막혀 있으면 Redis 에서 지운다.

```bash
redis-cli DEL tc:apikey-block:<ip> tc:apikey-fail:<ip>
```

### 6.2 기존 JWT 호출자 전환

지금 사용자 계정(JWT)으로 PLC 요청을 보내는 시스템을 API 키로 옮기는 절차다(docs/auth-and-audit-design.md §6.10).

1. **현황 파악** — 며칠 동안 감사 기록에서 JWT 로 부른 계정을 모은다.

   ```sql
   SELECT PRINCIPAL, COUNT(*) FROM EXCHANGED_MESSAGE_AUDIT
   WHERE MSG_TYPE = 'REQUEST' AND PRINCIPAL LIKE 'user:%' AND TIMESTAMP > NOW() - INTERVAL 7 DAY
   GROUP BY PRINCIPAL;
   ```
2. **시스템별 교체** — 클라이언트를 만들고(허용 opcode 는 그 계정이 실제로 쓴 opcode), 키를 발급해 전달하고, 그쪽 설정을 바꾼다. 감사의 `PRINCIPAL` 이 `client:…` 로 바뀌는지 본다.
3. **옛 계정 중지** — 7일 동안 그 계정의 JWT 호출이 없으면 사용자 사용 중지(`POST /api/v1/users/{id}/disable`). 문제가 생기면 다시 켠다.
4. **정리** — 전환 스위치는 **기본 꺼짐**이다(2026-10-30 정리 반영). PLC 요청은 API 키 클라이언트와 ADMIN·OPERATOR 사용자만 할 수 있고,
   사용자에게 `ROLE_CLIENT` 를 새로 줄 수 없다.
   - 1~3단계를 마치기 전에 이 버전을 배포한다면, 배포 직후 스위치를 켠다(재기동 없음, 모든 인스턴스에 5초 안에 반영).

     ```bash
     curl -X PUT -H "Authorization: Bearer …" -H 'Content-Type: application/json'           -d '{"on":true}' http://<tc>/api/v1/security/dispatch-any-user
     ```
   - 3단계까지 끝나면 `{"on":null}` 로 설정값(꺼짐)으로 되돌린다. 바꿀 때마다 설정 변경 이력에 남는다.
   - 꺼진 상태에서 옮기지 못한 시스템이 부르면 403(`ERR_403`, 권한이 없습니다: PLC 요청)이다.

## 7. 사용자와 권한

| 역할 | 할 수 있는 것 |
|---|---|
| ROLE_ADMIN | 전부(사용자·API 클라이언트·권한 모드 포함) |
| ROLE_OPERATOR | PLC·인터페이스·WebHook·전달 정의·스케줄의 등록·수정·삭제·제어, 감사·이력 조회, PLC 요청 |
| ROLE_VIEWER | 조회만 |

- 사용자 관리: `/api/v1/users`(관리자). 만들 때 초기 비밀번호를 정하면 첫 로그인에서 바꾸게 한다. 사용 중지·비밀번호 초기화는 그 사용자의 토큰을 즉시 끊는다.
- 마지막 활성 관리자는 중지·삭제·관리자 역할 제거를 할 수 없다. 자기 자신은 중지·삭제할 수 없다.
- **로그인 실패 제한**: 아이디별 15분에 5회, IP별 20회를 넘으면 15분 동안 429. 계정을 잠그지는 않는다. 풀려면:

  ```bash
  redis-cli DEL tc:login-block:user:<id> tc:login-fail:user:<id>
  ```
- **권한 검사 모드**: 처음에는 `AUDIT`(선언 없는 쓰기 API 를 기록만). 모든 API 에 선언이 있으므로 `ENFORCE` 로 바꿔도 된다. `PUT /api/v1/security/permission-mode {"mode":"ENFORCE"}` — 모든 인스턴스에 바로 반영되고 설정 변경 이력에 남는다.

## 8. 비상 복구

### 8.1 관리자가 아무도 로그인할 수 없을 때

재기동 없이 DB 에서 고친다.

1. 새 비밀번호의 해시를 만든다(`tools/BcryptHash.java`).
2. ```sql
   UPDATE user SET password = '<bcrypt 해시>', enabled = 1, password_must_change = 1,
          roles = 'ROLE_ADMIN' WHERE id = '<관리자 아이디>';
   ```
3. 로그인 실패 제한에 걸렸다면 Redis 의 `tc:login-block:*`, `tc:login-fail:*` 를 지운다.
4. 로그인해 비밀번호를 바꾼다. DB 를 직접 고친 일은 설정 변경 이력에 남지 않으므로, 복구 사유를 운영 기록에 남긴다.

### 8.2 커넥터 상태가 꼬였을 때

증상: 게시 상태인데 요청이 503 이거나 연결·끊김이 반복된다.

먼저 커넥터 조회의 `connection` 을 본다 — `status1` 은 운영자 의도(게시·해제)이고, 실제 연결은 `connection`(살아 있는 채널 수 `activeChannels`, 마지막 오류 `lastError`, 연결한 인스턴스)이다. `connection` 이 없으면 어느 인스턴스도 그 PLC 어댑터를 올리지 않은 것이다.

1. 커넥터를 해제한다(`POST /api/v1/plc-connector/control/release/byName/{name}`).
2. PLC 쪽에 옛 연결이 남았는지 확인하고(남았으면 끊거나 생존 확인 시간을 기다린다) 다시 게시한다.

게시를 여러 번 눌러도 연결이 더 열리지는 않는다(이미 돌고 있으면 무시).

## 9. 점검

| 무엇 | 어디 |
|---|---|
| 살아 있는지 | `GET /api/health`(인증 없음): `UP` / `DEGRADED`(Redis 없음) / `DOWN`(503, DB 없음) |
| 누가 무엇을 바꿨는지 | 설정 변경 이력 `POST /api/v1/config-history/search`, 표 `CONFIG_CHANGE_HIST`(365일 보존) |
| 누가 PLC 요청을 했는지 | `EXCHANGED_MESSAGE_AUDIT.PRINCIPAL`(`client:…`, `user:…`, `system`) |
| 로그인·로그아웃·비밀번호 변경 | `USER_HIST` |
| PLC 연결·끊김 | `PLC_CONNECTION_HIST`, 로그 `PLC 연결 끊김`, `PLC 응답 시간 초과로 연결을 닫습니다` |
| 스케줄 실행 | `SCHEDULE_JOB_EXEC_HIST`(실패면 `REMARK` 에 사유) |
| 경고 로그 키워드 | `API 키 만료 임박`, `인증 실패가 1분에`, `로그인 실패가`, `통과시킴`(Redis 문제) |
| PLC 연결 상태·주인 | `GET /api/v1/plc-connector/connection-states`(Redis `tc:plc-state:*`), 다중화면 Redis `tc:plc-owner:*`(주인), `tc:instance:*`(살아 있는 인스턴스와 내부 주소) |
| 다중화 로그 키워드 | `임대를 얻었습니다`, `다른 인스턴스가 가졌습니다`(강등), `요청을 그쪽으로 넘깁니다`, `소유권(Redis)에 닿지 않습니다`, `서명이 맞지 않는` |

## 10. 백업

- DB `tc` 전체(설정·이력·Quartz). 이력 표(`EXCHANGED_MESSAGE_AUDIT`, `CONFIG_CHANGE_HIST`, `USER_HIST`)가 가장 빨리 커진다.
- Redis 는 백업하지 않아도 된다(토큰·집계·캐시). 잃으면 사용자가 다시 로그인하고, 권한 검사 모드는 설정값으로 돌아간다.

## 11. 시험

- 단위 테스트: `./gradlew :tc:test`(인프라 불필요)
- e2e: `wow-tc-backend/e2e/README.md` — 실제 tc·vplc·DB·Redis 를 대상으로 151개 항목
