# 쌓이는 기록의 보관과 정리

- 작성일: 2026-11-01 (v0.5.0)
- 대상: `wow-tc-backend/tc` — 송수신 감사(`EXCHANGED_MESSAGE_AUDIT`), PLC 연결 이력(`PLC_CONNECTION_HIST`), 스케줄 Job 실행 이력(`SCHEDULE_JOB_EXEC_HIST`)
- 관련: [schedule-jobs.md](schedule-jobs.md)(정리 Job `DataTableFlashJob`), [redelivery-design.md](redelivery-design.md)(감사가 재전송의 원본), [operations.md](operations.md) §3.1

## 1. 문제

세 표는 요청이 없어도 스케줄로 계속 쌓인다.

- 1초 주기 PLC 수집 Job 하나: 실행 이력이 하루 약 8.6만 행, 감사가 REQUEST·RESPONSE(·DELIVERY)로 그 2~3배(17만~26만 행)이고 행마다 `MSG_JSON`(LONGTEXT)이 붙는다.
- 정리하지 않으면 몇 달 안에 표가 수천만 행이 된다 — 디스크, 기간 검색, 백업 시간이 함께 나빠진다.

예전 tc 는 `schedule-job-history-deletion-executor` 같은 정리 Job 을 **운영자가 등록**해야 지웠고, 인터페이스 › 이력 관리 화면에서 관리자가 날짜를 골라 직접 지울 수 있었다.

## 2. 검토 — 정리 Job 과 화면 삭제

### 2.1 오래된 기록을 지우는 것은 맞다. 다만 "운영자가 만들면"이 기본이면 안 된다

- 정리 Job 이 없으면 표가 끝없이 커진다(예전 문서도 "없으면 표가 계속 커진다"). 운영자가 등록을 잊는 순간 문제가 된다.
- 같은 tc 안에서 방식이 달랐다 — 설정 변경 이력(`CONFIG_CHANGE_HIST`)은 `tc.config-history.retention-days: 365` 로 시스템이 스스로 지우는데, 나머지 셋은 Job 을 만들어야 했다.
- Job 방식 자체는 장점이 있다 — 보관 일수·주기를 **재기동 없이** 화면에서 바꾼다(tc 재기동은 MES 요청을 끊는다).
- → **결정 R1**: Job 방식을 유지하되 tc 가 기동할 때 **기본 정리 Job 을 스스로 만든다**(§3). 운영자는 만드는 사람이 아니라 값을 조정하는 사람이 된다.

### 2.2 "일정 시간이 지나면 의미 없다"는 표마다 다르다

| 표 | 오래된 기록의 가치 | 기본 보관 |
|---|---|---|
| `SCHEDULE_JOB_EXEC_HIST` | 금방 의미가 없어진다 — 최근 실행·실패 확인용 | 30일 |
| `PLC_CONNECTION_HIST` | 장애 분석에 몇 주~몇 달 필요. 양은 적다 | 90일 |
| `EXCHANGED_MESSAGE_AUDIT` | **오래 남는다** — 결과 재전송의 원본(보관 기간 = 재전송 가능 기간), `PRINCIPAL` 로 누가 PLC 에 무엇을 썼는지 남는 유일한 기록 | 90일 |

감사는 지우기보다 **덜 쌓는 것**이 더 효과가 크다. 양의 대부분은 MES 요청이 아니라 스케줄 수집의 성공 기록이다 — "주기 수집 성공 감사 생략(실패만 남김)" 옵션을 뒷일로 둔다(§6).

### 2.3 화면에서 날짜를 골라 지우는 기능은 두지 않는다 (결정 R2)

- 보관 정책이 두 갈래가 된다 — Job 은 90일인데 누군가 화면에서 7일 전까지 지우면 재전송 가능 기간이 말없이 줄어든다.
- 되돌릴 수 없는 일을 클릭 한 번으로 한다. 큰 삭제는 HTTP 시간 초과에 걸린다.
- 감사를 감사받는 쪽이 지우는 모양이 된다.
- 필요한 경우는 "디스크가 차서 지금 줄여야 한다"는 비상뿐이다 → 정리 Job 의 보관 일수를 잠시 줄이고 다시 예약하거나, 뒷일로 "지금 실행" 버튼(§6)을 둔다. 같은 나눠 지우기 경로를 타고 실행 이력에 남는다.

### 2.4 코드에서 찾은 결함 (2026-11-01 고침)

| # | 결함 | 영향 | 고침 |
|---|---|---|---|
| R3 | 감사 `TIMESTAMP`·PLC 연결 이력 `EVENT_TIMESTAMP` 에 색인이 없었다(엔티티·migration 모두, 로컬 DB 에서도 확인) | 기간 검색이 표 전체를 훑는다. 정리의 `DELETE … WHERE 시각 < ? LIMIT n` 은 마지막 한 번이 표 전체를 훑으며, REPEATABLE READ 에서 훑은 행과 간격을 모두 잠가 그동안 **감사 INSERT(= PLC 요청 기록)가 막힐 수 있다** | migration `016_retention_indexes.sql`(온라인 생성), 엔티티 `@Index` |
| R4 | 정리가 시각 조건만으로 지웠다 | 위와 같음 | 기본 키 범위로 지운다(§4) |
| R5 | 옛 일괄 삭제가 남아 있었다 — `POST /api/v1/ifsvc/audit/delete/timestamp`, `deletePlcConnectionEventHistories`, 쓰이지 않는 `flushBySavingPeriod` 재정의 | 나누지 않은 한 번의 DELETE — 몇 달치에서 부르면 R3 이 훨씬 크게 생긴다 | 지움(새 화면은 부르지 않는다, 옛 API 호환 불필요) |

## 3. 기본 정리 Job (`CleanupJobBootstrap`)

tc 가 기동할 때(ApplicationRunner) **표마다, 그 표를 정리하는 Job 이 하나도 없으면** 정리 Job 을 만들고 예약한다.

| Job 이름 | 표 | 보관 일수(설정) |
|---|---|---|
| `cleanup-exchanged-message-audit` | `EXCHANGED_MESSAGE_AUDIT` | `tc.schedule.default-cleanup.days.exchanged-message-audit` (90) |
| `cleanup-plc-connection-hist` | `PLC_CONNECTION_HIST` | `…days.plc-connection-hist` (90) |
| `cleanup-schedule-job-exec-hist` | `SCHEDULE_JOB_EXEC_HIST` | `…days.schedule-job-exec-hist` (30) |

- 주기: `tc.schedule.default-cleanup.cron`(기본 `0 10 4 * * ?`, 매일 04:10). 끄기: `tc.schedule.default-cleanup.enabled`(`TC_SCHEDULE_DEFAULT_CLEANUP`, 기본 true).
- "그 표를 정리하는 Job" = 종류가 `DataTableFlashJob` 이고 `cleanupTarget` 이 그 표인 Job. 멈춤·사용 안 함이어도 있다고 본다 — 운영자가 만든 Job 을 건드리지 않는다.
- 만든 뒤 보관 일수·주기는 화면(운영 › 스케줄 Job)에서 고친다. 설정값은 처음 만들 때만 쓴다.
- **지운 Job 은 다음 기동 때 다시 만들어진다.** 정리를 멈추려면 지우지 말고 사용을 끈다.
- 다중화에서 두 서버가 함께 기동하면 한쪽은 같은 이름(409)으로 실패하고 경고 로그만 남긴다.
- 등록은 일반 등록과 같은 길(검사·설정 변경 이력)을 탄다.

## 4. 나눠 지우기 (`DataCleaner`)

1. `SELECT MAX(키) FROM 표 WHERE 시각 < 기준` — 잠그지 않는 읽기. 시각 색인(R3)으로 빠르다.
2. `DELETE FROM 표 WHERE 키 <= ? AND 시각 < ? LIMIT n` 을 지운 수가 n 보다 적을 때까지 되풀이, 문장마다 커밋(`tc.schedule.cleanup-batch-size`, 기본 5000).

- 기본 키 범위로 지우므로 잠그는 행이 지울 범위 안으로 줄고, 새 행은 범위 밖(더 큰 키)에 들어가 막히지 않는다.
- 시각 조건도 함께 둔다 — 감사의 시각은 요청 본문의 `timestamp` 라 키 순서와 다를 수 있다. 기준보다 새 행은 키가 작아도 지우지 않는다.
- 키는 각 표의 자동 증가 기본 키다(`CleanupTarget`: 감사·PLC 연결 이력 `ID`, 실행 이력 `SEQ_NO`).

## 5. 확인 (2026-11-01)

- 단위: `CleanupJobBootstrapTest`(같은 표를 정리하는 Job 판별), 기존 `ScheduleJobConcurrencyTest`·`ScheduleJobValidatorTest`. `./gradlew :tc:test` 통과.
- 시험 DB `tc_boot` 로 두 번째 tc(:8097, 주기 15초로 덮어씀):
  - migration 016 으로 `ix_ema_timestamp`·`ix_pch_event_timestamp` 생성.
  - 기동하자 기본 정리 Job 3개가 만들어지고 예약됨. 다시 기동하면 만들지 않음.
  - 감사에 200·100·95일 전 행과 1일 전 행을 넣음(95일 전 행은 1일 전 행보다 키가 큼) → 90일 지난 3건만 지워지고 1일 전 행은 남음. 실행 이력 SUCCESS, 비고 `EXCHANGED_MESSAGE_AUDIT: 90일 지난 3건 삭제 (기준 …)`.
  - 시험 뒤 `tc_boot` 의 정리 Job 은 지웠다.

## 6. 덧붙인 것 (2026-11-01 둘째)

### 6.1 주기 수집의 성공 기록 생략 — PLC 요청 Job 속성 `auditMode`

| 값 | 송수신 기록 |
|---|---|
| `ALL`(기본, 없으면 이것) | 지금처럼 모두 — REQUEST·RESPONSE·DELIVERY |
| `FAILURE_ONLY` | **실패만** — 성공한 실행은 남기지 않고, 실패하면 REQUEST 와 RESPONSE(PLC 오류 종료 코드) 또는 ERROR, 실패한 DELIVERY 를 남긴다 |

- Job 단위로 둔다 — 쌓이는 양의 대부분이 짧은 주기 수집의 성공 기록이고, MES 가 보내는 요청은 그대로 모두 남겨야 해서 인터페이스 단위로 두지 않았다.
- 동작: Job 이 요청에 "실패만" 표시(`TcMessageRequest.auditFailuresOnly`, 서버 안에서만)를 단다. 감사 인터셉터는 REQUEST 를 응답을 볼 때까지 붙잡아 두었다가 성공이면 버리고 실패면 함께 남긴다. 전달 서비스는 성공한 DELIVERY 를 남기지 않는다.
- 실패한 전달은 그 DELIVERY 한 줄(보낸 응답 전체를 담는다)로 다시 보낼 수 있다. **성공한 실행은 기록이 없으므로 다시 보낼 수 없고, 메시지 모니터에도 나오지 않는다** — 실행 이력(비고 `… (성공은 송수신 기록 생략)`)으로 돌았는지 본다.
- 저장 검사: `ALL`·`FAILURE_ONLY` 말고는 400. READ 인터페이스 Job 에도 둘 수 있다(요청 data 로 넘기지 않는다).
- 화면: 종류를 PLC 요청으로 고르면 속성 표에 `auditMode`(모두 남김·실패만 남김)가 기본값 ALL 로 들어간다.

### 6.2 지금 실행

- `POST /api/v1/schedule-job/monitoring/runScheduleJobNow/{name}`(권한 X). 주기는 그대로 두고 지금 한 번 돈다(Quartz `triggerJob`). 실행 중이면 끝난 뒤에 돈다(겹쳐 돌지 않음).
- 없는 Job 404, 예약되지 않은 Job(사용 안 함·예약 취소) 409. 설정 변경 이력에 `run-now` 로 남고, 결과는 실행 이력에 남는다.
- 화면: 운영 › 스케줄 Job 의 작업 메뉴 "지금 실행"(확인 → "실행을 요청했습니다"). 정리 Job 을 비상으로 돌릴 때(§2.3) 이것을 쓴다.

### 6.3 감사 삭제 API 없앰 (결정 R2)

- `DELETE /api/v1/ifsvc/audit/delete/{id}`, `POST /api/v1/ifsvc/audit/delete`(ID 목록)를 지웠다(§2.4 의 시각 기준 삭제와 함께 세 개 모두). 감사는 조회만 되고, 지우는 것은 보관 정리 Job 뿐이다.
- 새 화면은 이 API 를 부르지 않았다. 지운 기록이 설정 변경 이력에 남지 않던 문제도 함께 없어진다.

### 6.4 확인

- e2e `test_retention.py`(18개 항목) — 시험 DB `tc_boot` 의 두 번째 tc(:8097)와 가짜 PLC(`fake_plc.py`)로: auditMode 저장 검사·종류 정의, FAILURE_ONLY 성공 거래에 기록 없음·비고, 커넥터를 멈춰 실패시키면 REQUEST·ERROR 가 남음, 지금 실행(이력 1건 늘고 SUCCESS, 404·409, 설정 변경 이력), 감사 삭제 API 세 개가 404·405. 모두 통과.
- 화면 e2e `schedule-run-now.spec.ts`(지금 실행·auditMode 칸), 기존 `ops.spec.ts` 스케줄 Job 시험(속성 수가 2 로 바뀜)을 같은 인스턴스로 통과.
- 시험 뒤 `tc_boot` 의 시험 계정·Job 은 지웠다.

### 6.5 남은 것

| 일 | 메모 |
|---|---|
| 파티션 | 양이 정말 커지면 `TIMESTAMP` 기준 월·일 파티션 + `DROP PARTITION`. 기본 키를 `(ID, TIMESTAMP)` 로 바꾸는 표 구조 변경이라 실제 양을 본 뒤 정한다 |

## 7. 운영 반영

- **배포 전에** migration `016_retention_indexes.sql` 을 돌린다(온라인 생성 — 앱을 멈추지 않아도 된다). 돌리지 않으면 `ddl-auto=update` 가 기동하면서 색인을 만들어 큰 표에서 기동이 늦어진다.
- jar 교체라 **재기동이 필요**하다. 재기동하면 기본 정리 Job 이 만들어진다 — 이미 쌓인 기록이 많으면 첫 실행(04:10)에 오래 걸릴 수 있다(나눠 지우므로 기록은 막히지 않는다).
- 새 설정: `tc.schedule.default-cleanup.*`(기동 때 읽는다).
- 지운 API: `POST /api/v1/ifsvc/audit/delete/timestamp`, `DELETE /api/v1/ifsvc/audit/delete/{id}`, `POST /api/v1/ifsvc/audit/delete`.
- 새 API: `POST /api/v1/schedule-job/monitoring/runScheduleJobNow/{name}`. 새 Job 속성: PLC 요청 `auditMode`.
