# 스케줄 Job — 속성·실행 이력

- 작성일: 2026-11-01
- 관련: [frontend-design.md](frontend-design.md) §5(화면 목록), [plc-ha-design.md](plc-ha-design.md) §9.2(겹쳐 돌지 않음), migration `015_schedule_job_monitoring.sql`

## 1. 개념

스케줄 Job 은 운영 중에 수시로 추가·수정·삭제한다. Job 마다 **속성**(키·값, 표 `SCHEDULE_JOB_DETAIL`)을 정의하면, Job 이 실행될 때 그 값을 읽어 일한다. 주기적으로 저절로 돌기 때문에 **실행 이력**(`SCHEDULE_JOB_EXEC_HIST`)으로 정상 동작을 확인할 수 있어야 한다.

| 표 | 내용 |
|---|---|
| `SCHEDULE_JOB` | 이름(Quartz 키)·종류(Job 클래스)·주기(cron 또는 시작 지연 + 간격)·사용·설명·상태 |
| `SCHEDULE_JOB_DETAIL` | 속성 — 키·값·값 형·설명 |
| `SCHEDULE_JOB_EXEC_HIST` | 실행마다 한 행 — 실행 시각·결과(SUCCESS·FAIL)·다음 실행·실행 서버(`INSTANCE_ID`)·걸린 시간(`ELAPSED_MS`)·비고 |

## 2. Job 종류 (`ScheduleJobKind`)

등록할 수 있는 Job 은 아래 종류뿐이다. 화면에서 클래스 이름을 받지 않고, 서버가 다른 클래스를 거절한다. 화면은 `GET /api/v1/schedule-job/management/job-kinds` 로 종류와 속성 정의를 받아 속성 표의 틀로 쓴다.

| 종류 | 클래스 | 속성 | 비고(성공) |
|---|---|---|---|
| PLC 요청 | `PlcGeneralScheduleJob` | `interfaceName`(필수) + WRITE 인터페이스면 **점 이름마다 쓸 값**(모든 점). READ 는 `interfaceName` 만 | `plc.x 성공 (trx …) — 값 n개, 전달 m곳` |
| 오래된 기록 정리 | `DataTableFlashJob` | `cleanupTarget`(EXCHANGED_MESSAGE_AUDIT·PLC_CONNECTION_HIST·SCHEDULE_JOB_EXEC_HIST), `savingDays`(1 이상) | `표: n일 지난 k건 삭제 (기준 …)` |

- PLC 요청의 결과는 인터페이스의 전달 정의대로 보낸다. 비고의 거래 ID 로 송수신 기록을 찾는다(화면의 "송수신 기록 보기").
- 정리는 `DELETE … LIMIT n` 을 되풀이해 나눠 지운다(`tc.schedule.cleanup-batch-size`, 기본 5000). 실행 이력 자체도 이 Job 으로 정리한다 — 1분 주기 Job 하나가 1년에 약 52만 행을 쌓는다.

## 3. 저장할 때 검사 (`ScheduleJobValidator`, 틀리면 400)

- 이름: 영숫자·`_ . -` 100자까지. 같은 이름은 409. 이름·종류는 등록 뒤 바꾸지 않는다.
- 주기: cron 은 Quartz 식, 간격은 1000ms 이상.
- 속성: 키가 비거나 겹치면 안 된다. 값 형은 `string`·`int`·`long` — JDBC JobStore 가 `useProperties=true` 라 JobDataMap 에 문자열만 들어간다(예전 `ref`·`list`·`map` 은 예약할 때 실패했다). `int`·`long` 은 그 형으로 읽혀야 한다.
- 종류별: 필수 속성, PLC 는 인터페이스가 있고 나머지 속성이 그 점과 맞는지, 정리는 표·보관 일수와 다른 속성 없음.
- 수정은 속성을 **통째로 바꾼다** — 목록에서 빠진 키는 지운다.

## 4. 실행과 이력 (`ScheduleJob`)

- `executeJob` 이 정상으로 끝나면 SUCCESS(돌려준 요약이 비고), 예외면 FAIL(오류 코드·사유가 비고). 예외는 Quartz 로 넘기지 않는다.
- 이력 저장이 실패해도 Job 은 계속 돈다(로그만).
- 실행할 때 `SCHEDULE_JOB.SCHED_NAME` 을 덮어쓰지 않는다(예전: 시작에 스케줄러 이름, 끝에 `NO_ASSIGNED` — 등록한 값이 사라졌고, 다중화에서 모든 노드의 스케줄러 이름이 같아 어느 서버가 돌렸는지 알 수 없었다). 실행 서버는 이력의 `INSTANCE_ID`(Quartz instanceId)로 본다.
- Job 은 `AutowiringSpringBeanJobFactory` 가 만들며 `@Autowired` 필드를 채운다.

## 5. 화면 (운영 › 스케줄 Job)

- 목록: 이름·종류·주기·상태·**최근 실행·최근 결과(비고 툴팁)·최근 24시간 실패**(`tc.schedule.recent-fail-hours`)·**다음 실행**·사용·설명, 작업(수정·멈춤/재개·예약 취소·다시 예약·삭제).
- 행을 고르면 아래에 탭 두 개.
  - **속성**: 키·값·값 형·설명 표. 종류의 속성은 키가 고정이고 필수는 지울 수 없다. PLC 요청이면 속성 추가·"쓸 점 채우기"(WRITE 인터페이스의 점). 저장하면 다시 예약한다.
  - **실행 이력**: 서버 쪽 쪽 넘김(최신순), 결과·기간으로 걸러 보기, 자동 새로 고침(5·30초), 실패 행 강조. 행을 누르면 비고 전체와 송수신 기록.
- 등록·수정 창: 이름·종류·주기·속성 표·사용·설명. 종류를 고르면 그 종류의 속성이 기본값과 함께 들어간다.

### 5.1 운영 › 스케줄 Job 실행 이력 (`tc.ops.schedulehist`, `/ops/schedule-job-histories`)

모든 Job 의 실행 이력을 한 표로 본다(권한은 스케줄 Job 과 같은 `tc.schedule`).

- 필터바(송수신 기록과 같은 모양 — 라벨 위, 검색·초기화 오른쪽): 통합검색(Job 이름·설명), Job 상태(기본 전체), 실행 시작·끝, 실행 결과 토글(전체·성공·실패, 기본 전체), 최근 24시간 스위치(켜면 실행 기간 대신 지금부터 24시간 전까지 — 기간 칸은 잠긴다).
- 표: 번호·스케줄 Job·실행 결과·걸린 시간·실행 서버·실행 시간·다음 실행·비고. 서버 쪽 쪽 넘김(50·100·200·500), 최신순, 실패 행은 붉게. 행을 누르면 비고 전체와 송수신 기록.
- Job 상태를 고르면 지금 있는 Job 의 이력만 나온다. 글만 주면 지운 Job 의 이력도 이름으로 찾는다.

## 6. API

| 경로 | 내용 |
|---|---|
| `GET /api/v1/schedule-job/management/job-kinds` | 종류와 속성 정의 |
| `POST /api/v1/schedule-job/management/search` | 목록 — `kind`·`lastExecutionTime`·`lastResult`·`lastRemark`·`lastInstanceId`·`lastElapsedMs`·`nextFireTime`·`recentFailCount` 를 채운다 |
| `POST /api/v1/schedule-job/management/register`·`/update` | 저장(검사, 속성 통째로) — 예약은 따로 `monitoring/rescheduleJob/{name}` |
| `POST /api/v1/schedule-job/management/exec-histories/query` | 이력 `{jobName, jobSearch, jobState, from, to, result, page, size}` → `{items, total, page, size}`, 최신순, 쪽 크기 최대 500. `jobName` 은 같음, `jobSearch` 는 Job 이름·설명에 든 글(대소문자 무시, 지운 Job 은 이름으로), `jobState` 는 지금 있는 Job 의 상태 |

## 7. 운영 반영

- migration `015_schedule_job_monitoring.sql` — 이력 칼럼·색인, 정리 Job 속성을 새 정의로 옮김(`serviceBeanName` 등 → `cleanupTarget`), 빈 Job(`PlcConnectorSpyScheduleJob`, 클래스 삭제) 행 삭제, `NO_ASSIGNED` 로 덮인 `SCHED_NAME` 되돌림, 값 형 소문자.
- jar 교체라 **tc 재기동이 필요**하다. 재기동하면 모든 Job 을 새 속성으로 다시 예약한다.
- 새 설정: `tc.schedule.cleanup-batch-size`(기본 5000, `TC_SCHEDULE_CLEANUP_BATCH_SIZE`), `tc.schedule.recent-fail-hours`(기본 24) — 기동 때 읽는다.
