# DB_TABLE 결과 전달

> 할 일 #10 (2026-10-31). PLC 결과(요청 응답·스케줄 수집)를 지정한 DB 테이블에 한 행씩 기록하는 네 번째 전달 방식이다.
> 바로 응답, CALLBACK_URL, WEBHOOK 과 같은 전달 정의(INTERFACE_DELIVERY)로 켠다. 설계 배경은 [dispatch-redesign.md](dispatch-redesign.md) §3.3.

## 1. 결정

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| T1 | 어느 DB 에 쓰나 | 관리 API 로 **런타임 등록한 DB 연결**. tc 자체 DB 는 예약 이름 `tc` 로 등록 없이 쓴다 | 설정 파일 등록은 DB 를 더할 때마다 재기동이 필요하다 |
| T2 | 행 단위 | **결과 1건 = 1행.** 공통 정보는 칼럼, 데이터 값은 `PAYLOAD` 에 JSON | 인터페이스의 데이터 속성이 바뀌어도 테이블·매핑을 고칠 필요가 없다. 받는 쪽은 필요한 값만 `JSON_VALUE`·가상 칼럼으로 꺼낸다 |
| T3 | 테이블은 누가 만드나 | **받는 시스템이 만든다.** tc 는 전달 정의를 등록할 때 표준 칼럼이 다 있는지 JDBC 메타데이터로 확인만 한다 | tc 가 남의 DB 에 DDL 을 하지 않는다 |
| T4 | 실패 결과 | **기록한다**(`SUCCESS=0`, `ERROR_CODE`, `ERROR_MESSAGE`). 거르려면 전달 정의의 `deliverOn: SUCCESS` | 받는 쪽이 "값이 안 온 것"과 "읽기 실패"를 구별한다 |
| T5 | 기록 실패 | 다른 채널과 같이 **전달 감사(DELIVERY)와 경고 로그만**, 재시도 없음 | 재시도(outbox)는 #11 에서 정한다 |

칼럼 매핑(데이터 속성 → 칼럼)은 만들지 않았다. 받는 쪽이 꼭 칼럼으로 받아야 한다고 하면 전달 정의에 선택 기능으로 붙인다.

## 2. 표준 테이블

받는 시스템이 만든다. 이름은 자유(전달 정의의 `tableName`, 스키마를 붙이려면 `schema.table`). 아래 8개 칼럼이 있어야 하고, 다른 칼럼(키, 넣은 시각 등)은 있어도 된다.

```sql
-- MariaDB / MySQL
CREATE TABLE plc_result (
    ID             BIGINT AUTO_INCREMENT PRIMARY KEY,
    TRANSACTION_ID VARCHAR(100),
    INTERFACE_NAME VARCHAR(200),          -- opcode
    PLC_NAME       VARCHAR(200),
    SUCCESS        BOOLEAN       NOT NULL,
    ERROR_CODE     VARCHAR(100),          -- 예: ERR_503_TC_PLC_UNAVAILABLE
    ERROR_MESSAGE  VARCHAR(1000),         -- 1000자에서 자른다
    COLLECTED_AT   DATETIME(3)   NOT NULL,-- 응답 시각(tc)
    PAYLOAD        LONGTEXT,              -- 응답 data JSON, 예: {"d100":77,"d101":1}
    INSERTED_AT    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX IX_PLC_RESULT_IF_AT (INTERFACE_NAME, COLLECTED_AT)
);

-- 받는 쪽에서 자주 쓰는 값을 칼럼처럼 쓰려면(인덱스 가능)
ALTER TABLE plc_result ADD COLUMN D100 INT AS (JSON_VALUE(PAYLOAD, '$.d100')) VIRTUAL;
```

**주의 — 데이터 속성 이름은 받는 쪽과의 계약이다.** 인터페이스에서 속성 이름을 바꾸면 JSON 키가 바뀌고, 그 키를 읽던 쿼리는 오류 없이 NULL 을 받는다. 바꾸기 전에 받는 쪽과 맞추고, 바뀐 내역은 설정 변경 이력(INTERFACE)으로 확인한다.

## 3. DB 연결

`/api/v1/db-connections` — 등록·수정·삭제는 관리자, 조회는 운영자도(전달 정의를 만들 때 이름을 고르도록). 모두 재기동 없이 반영된다.

| 항목 | 내용 |
|---|---|
| `name` | 영문 소문자·숫자·밑줄·하이픈. `tc` 는 예약 |
| `jdbcUrl` | `tc.db-connection.allowed-url-prefixes`(기본 `jdbc:mariadb:`, `jdbc:mysql:`)로 시작해야 한다. tc 에 드라이버가 있는 DB 만 넣는다 |
| `username`, `password` | 비밀번호는 받기만 하고 돌려주지 않는다(`passwordSet` 만). 수정할 때 비우면 기존 값 유지 |
| `maxPoolSize` | 1~20, 기본 2 |
| `use` | 끄면 그 연결로의 기록이 실패(감사에 남음)하고, 그때 풀을 닫는다 |

- **금지 옵션**: `allowLocalInfile`, `allowLoadLocalInfile`, `allowUrlInLocalInfile`, `autoDeserialize`, `allowMultiQueries`, `init`, `socketFactory`. 대상 DB 서버가 tc 서버의 파일을 읽어 가거나, 받은 값을 역직렬화하게 만드는 옵션이다(`tc.db-connection.forbidden-url-options`).
- **풀**: 연결마다 작은 Hikari 풀(최소 0, 유휴 60초). 기록할 때마다 저장된 연결 정보와 비교해 바뀌었으면 새로 만든다 — 다른 tc 인스턴스에서 바꾼 값도 다음 기록부터 반영된다. 대상 DB 가 내려가 있어도 tc 는 멈추지 않고 그 기록만 실패한다(연결 대기 `tc.db-connection.connect-timeout`, 기본 5초).
- **시험**: `POST /api/v1/db-connections/{name}/test` (`tc` 도 된다).
- **삭제**: 이 연결을 쓰는 DB_TABLE 전달 정의가 있으면 409.
- **변경 이력**: 설정 변경 이력 `DB_CONNECTION` 으로 남는다. 비밀번호는 남기지 않는다.

### 3.1 비밀번호 암호화 (`TC_SECRET_KEY`)

- AES-GCM, 키는 환경 변수 `TC_SECRET_KEY`(Base64 16·24·32바이트, 32 권장). 저장 형식 `v1:` + Base64(IV + 암호문).
- 키가 없어도 tc 는 뜬다. 비밀번호가 있는 연결을 등록하거나 쓸 때만 400(`TC_SECRET_KEY 가 설정되지 않아…`).
- **키를 바꾸거나 잃으면 저장된 비밀번호를 풀 수 없다** — 각 연결을 비밀번호와 함께 다시 수정(PUT)한다. 키는 기동 때 읽으므로 바꾸려면 재기동한다.
- 대상 DB 계정은 그 테이블에 INSERT 만 되는 계정을 권한다.

## 4. 전달 정의

```json
POST /api/v1/ifsvc/delivery
{ "interfaceName": "line1.temp.read", "deliveryType": "DB_TABLE",
  "targetName": "mes-db", "tableName": "plc_result", "deliverOn": "ALL", "onChangeOnly": true }
```

- 등록·수정할 때 연결이 있고 사용 중인지, 테이블이 있고 표준 칼럼이 다 있는지 대상 DB 에 실제로 물어본다. 대상 DB 가 내려가 있으면 등록이 400 이다.
- 테이블 이름은 `[A-Za-z_][A-Za-z0-9_]*` 에 스키마 하나까지만 받는다. SQL 에 들어가는 것은 이 이름뿐이고 값은 모두 바인드 변수다. 기록할 때도 모양을 다시 확인한다.
- 한 인터페이스에 같은 DB 연결로는 정의 하나(테이블 하나)만 둔다(유니크 제약이 인터페이스·유형·대상 이름까지만 본다).
- 다른 전달 정의와 같이, 인터페이스에 정의가 하나라도 있으면 정의대로만 보낸다(레거시 WebHook 목록은 쓰지 않는다).
- `onChangeOnly: true` 면 PLC 값이 직전과 같을 때 행을 넣지 않는다 — 스케줄 수집에서 같은 값이 쌓이지 않게.
- 결과 감사: `MSG_TYPE=DELIVERY`, `RECEIVER=DB_TABLE:{연결}:{테이블}`, 성공이면 `DESCRIPTION=OK`.

## 5. 시험

- 단위: `SecretCipherTest`(암호화·키 오류), `DbConnectionRulesTest`(JDBC 주소·금지 옵션·테이블 이름).
- e2e: `wow-tc-backend/e2e/test_db_table.py`(33개 항목) — 로컬 tc DB 를 외부 DB 로 다시 등록해 쓴다. tc 를 `TC_SECRET_KEY` 와 함께 띄워야 한다.

## 6. 운영 반영

1. `migration/006_db_table_delivery.sql` (ddl-auto 를 끈 환경).
2. `TC_SECRET_KEY` 를 만들어 환경 변수로 넣고 재기동 — 다음 계획된 재기동 때 함께. 키는 DB 백업과 따로 보관한다.
3. 받는 시스템에 표준 테이블과 INSERT 전용 계정을 만든다.
4. DB 연결 등록 → 시험 → 전달 정의 등록.
