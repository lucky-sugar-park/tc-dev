# 백엔드 할 일

> 기준일: 2026-10-30. 대상: `wow-tc-backend`(tc, vplc).
> 상태 표기: ⬜ 할 일 · 🔄 진행 중 · ✅ 완료 · ⏸ 조건 대기

## 바로 할 것

| # | 일 | 상태 | 메모 |
|---|---|---|---|
| 1 | 작업 트리 커밋·push | ✅ | `81287cc` (v0.1.0 에 push) |
| 2 | access 토큰 수명 30분 적용 (결정 D6) | ✅ | `jwt.access-token-validity-ms: 1800000`. 로컬에서 토큰 `exp - iat = 1800` 확인. 운영 반영은 다음 계획된 재기동 때 |
| 3 | 아직 시험하지 않은 것 점검 | ✅ | 아래 "3번 결과" |

### 3번 결과 (2026-10-30)

빈 시험 DB(`tc_boot`, `tc` 의 스키마만 복사)로 두 번째 tc(:8097)를 띄워 시험했다.

| 항목 | 결과 |
|---|---|
| 최초 관리자 생성 | ✅ 빈 표에서 생성, 첫 로그인 비밀번호 변경 강제(403 → 변경 → 200), SYSTEM 행위자로 이력, 재기동해도 다시 만들지 않음 |
| 설정 변경 이력 보존 정리 | ✅ 보존 1일·20초 주기로 돌려 3일 전 이력이 지워지고 `purge:n` 이 남음 |
| Redis 장애 시 fail-open | ✅ Redis 없이 기동됨. API 키 PLC 요청은 인증·사용량 검사를 통과해 디스패처까지 감(경고 로그 `통과시킴`), 대입 차단 집계도 통과. 사용자 로그인은 토큰 저장소가 Redis 라 안 됨(설계상) |
| 비동기 어댑터 큐(`asyncUse` 타이머) | ⚠️ **호출하는 곳이 없는 죽은 코드**다. `request(PlcReqAndResHolder, callback)` 를 부르는 코드가 없다. 비동기 요청(`/request`)은 디스패처 실행기로 처리된다. → #15 에서 지움 |

## 다음 큰 덩어리

| # | 일 | 상태 | 메모 |
|---|---|---|---|
| 4 | PLC 다중화 구현 | 🔄 | [plc-ha-design.md](plc-ha-design.md) §10. **1단계 완료**(아래). 결정 확정(2026-10-31, H6 현장 확인 대기). 1-1~4단계 완료(`tc.plc.ownership.enabled` 기본 꺼짐), 로컬 두 대 시험(정상 종료 이어받기 0.4초, 강제 종료 9.0초). 운영 절차는 [operations.md](operations.md) §4.2. 남은 것: 실제 다중 서버에서 Redis 장애·차단 시험, handover API(필요해지면) |
| 5 | 저장소에 남는 테스트 | ✅ | 단위 테스트 26개(`./gradlew :tc:test`, 인프라 불필요), e2e 묶음 `wow-tc-backend/e2e/`(143개 항목 — 7번에서 6개 추가, README 에 준비·실행 방법) |
| 6 | 운영 문서 | ✅ | [operations.md](operations.md) — 설정·재기동 필요 항목, migration 순서, 최초 관리자, 재기동 절차, PLC 설정, API 키 운영·전환, 비상 복구, 점검. 해시 도구 `wow-tc-backend/tools/BcryptHash.java` |

### 4번 1단계 결과 (2026-10-30)

| 항목 | 내용 | 확인 |
|---|---|---|
| 헬스 체크 | `GET /api/health`(인증 없음): UP / DEGRADED(Redis 없음, 200) / DOWN(DB 없음, 503). 확인마다 2초 상한 | ✅ e2e |
| 정상 종료 | `server.shutdown=graceful` 은 원래 있었다. 단계별 상한 25초(`spring.lifecycle.timeout-per-shutdown-phase`) 추가 | ✅ |
| 종료 순서 | PLC 연결 닫기를 `@PreDestroy` 에서 `PlcShutdownLifecycle`(SmartLifecycle, 웹 서버 뒤)로 옮김 → Quartz → 웹 서버(진행 중 요청 마침) → PLC 연결 닫기 | ✅ JMX 로 정상 종료를 일으켜, 진행 중 PLC 요청이 200 으로 끝나고 로그 순서와 커넥터 PUBLISHED 유지 확인 |

## 조건이 갖춰지면 할 것

| # | 일 | 상태 | 조건·메모 |
|---|---|---|---|
| 7 | 인증 7단계: 전환 정리 | ✅ | 코드 정리 완료(2026-10-30). 전환 스위치를 **런타임 옵션(기본 꺼짐)**으로 바꿔, 옮기지 못한 시스템이 드러나면 재기동 없이 켠다. PLC 요청 권한은 API 키 주체에만, 사용자 역할은 ADMIN·OPERATOR·VIEWER 만. 실제 호출 시스템 전환은 운영 절차([operations.md](operations.md) §6.2, 아래 #16) |
| 8 | framework 다음 버전 반영 | ✅ | framework-core **0.8.46** 배포·tc 반영 완료(2026-10-31). 명세 [framework-core-0.8.46-changes.md](framework-core-0.8.46-changes.md) §5 에 결과 |
| 9 | 상태를 바꾸는 GET 을 POST·DELETE 로 (결정 D5) | ✅ | 29개 — 경로는 그대로, 삭제는 DELETE·제어·사용 변경·연결 시험은 POST, 예전 GET 은 405(2026-10-31). e2e `test_http_methods.py`. 새 프론트는 이 메서드로 만든다 |
| 10 | DB_TABLE 결과 전달 채널 | ✅ | [db-table-delivery.md](db-table-delivery.md). 런타임 등록 DB 연결(비밀번호 AES-GCM, `TC_SECRET_KEY`), 결과 1건 = 1행(공통 칼럼 + `PAYLOAD` JSON), 테이블은 받는 쪽이 만들고 tc 는 검증, 실패도 기록. 운영 반영에 `TC_SECRET_KEY`·migration 006 필요 |
| 11 | 결과 재전송 — 관리자가 감사 기록을 골라 수동으로(성공·실패 무관) | ✅ 백엔드 | [redelivery-design.md](redelivery-design.md)(2026-10-31). 자동 재시도(outbox) 대신. 새 표 없이 송수신 감사로 — DELIVERY 감사에 보낸 응답 전체를 남긴다. `POST /api/v1/ifsvc/redelivery/{deliveries,responses}`, 관리자만. 기존 버그(`WebHookManager` NPE)도 고침. 화면은 프론트 재작성 때. 운영 반영은 migration·재기동 없음(배포 때 함께) |

## 장기 과제

| # | 일 | 상태 |
|---|---|---|
| 12 | PLC 보강: 주기적 연결 상태 확인, 4E 프레임, 응답 길이 검증, 포트 여러 개 병렬 시험 | ⬜ |
| 13 | Modbus·OPC UA 어댑터(tc, vplc) | ⬜ |
| 14 | 로컬 시험 데이터 정리(`plc-fake`, `e2e-*`·`hist-*`·`redis-down` 클라이언트, 시험 이력, 시험 DB `tc_boot`) | ⬜ |
| 15 | 비동기 어댑터 큐(죽은 코드) 제거 | ✅ | 어댑터의 타이머·큐·`request(holder, callback)`, `PlcReqAndResHolder`·`PlcRequestCallback`, 커넥터 설정 `asyncRequestUse`·`asyncTimer*`(API 필드 포함) 제거(2026-10-31). DB 칼럼은 남긴다 — `migration/007` 이 NULL 허용으로, DROP 은 주석 |
| 16 | 운영 환경의 실제 호출 시스템 API 키 전환(감사 `PRINCIPAL` 로 대상 확인 → 키 발급·교체 → 옛 계정 중지) | ⬜ 운영 작업 — 이 버전 배포 전 전환이 안 끝났으면 배포 직후 전환 스위치를 켠다 |

## 진행 기록

- 2026-10-30: 문서 작성. 1~6번 진행 — 1·2·3·5·6 완료, 4는 1단계까지 완료. 3번에서 비동기 어댑터 큐가 죽은 코드임을 찾아 #15 추가.
- 2026-10-30: 7번 완료(코드 정리). 전환 스위치를 런타임 옵션으로, 실제 전환은 운영 작업 #16 으로 분리. e2e 138개 항목 통과.
- 2026-10-31: 8번 완료 — framework-core 0.8.46(SearchSpec·JwtFilter·공개 경로) 반영, `SearchSpecs` 우회 제거, e2e 151개 항목 통과.
- 2026-10-31: 9번 완료 — 상태를 바꾸는 GET 29개를 DELETE·POST 로. 기존 프론트는 mes-dev 프론트 패턴으로 다시 만들 예정이라 GET 을 남기지 않음. e2e 201개 항목 통과(정상 종료 시험 제외).
- 2026-10-31: 10번 완료 — DB_TABLE 전달. 결정 T1~T5(db-table-delivery.md §1). 단위 테스트 36개, e2e 234개 항목 통과(정상 종료 시험 제외). 스케줄 Job 도 같은 전달 경로를 타지만 e2e 는 요청 경로만 확인.
- 2026-10-31: 11번 백엔드 완료 — 관리자 결과 재전송. e2e 272개 항목 통과.
- 2026-10-31: Eclipse 직접 기동 실패 수정 — 이름 없는 `@PathVariable`·`@Param` 13곳에 이름을 적고 `ParameterNamesTest` 로 막음. `-parameters` 없이 빌드한 jar 로 e2e 통과.
- 2026-10-31: 없는 대상에 500 이던 관리 API 15개를 404(`ERR_404`)로 — PLC 커넥터 제어·연결 시험, 인터페이스 조회·사용 변경, 송수신 기록 조회. 없는 스케줄 작업의 상세는 빈 목록, 없는 커넥터 이름 삭제는 200(ID 삭제와 같이). 예전 조회 API 중 없는 대상에 빈 200 을 주는 것은 그대로 둠(새 프론트에서 정리).
- 2026-10-31: 15번 완료 — 비동기 어댑터 큐 제거. 커넥터 API 의 `asyncRequestUse`·`asyncTimerStartDelay`·`asyncTimerRunInterval` 도 없앰(예전 프론트가 보내도 무시된다).
- 2026-10-31: vplc 의 상태를 바꾸는 GET 14개를 DELETE·POST 로(경로는 그대로, 예전 GET 은 405), 없는 가상 PLC 는 500 대신 404. e2e `test_vplc_http_methods.py`(31개 항목).
- 2026-10-31: 11번 보완 — 비동기 요청의 callback 주소를 REQUEST 감사 `DESCRIPTION`(`pushUrl: 주소`)에 남기고, 응답 기록으로 callback 에 다시 보낼 수 있게(redelivery-design.md §2.2, R12). 동기 요청은 `pushResult` 를 무시.
- 2026-10-31: 4번 다중화 결정 확정 — H1~H5·H7~H10 은 plc-ha-design.md §9.1·§9.2 검토안대로, H6(PLC 생존 확인 설정값)은 현장 확인 대기. 스케줄 Job 겹침·복구(H10) 추가.
- 2026-10-31: 4번 1-1 단계 완료 — WebHook 캐시 10초 만료·스레드 안전(H7), 스케줄 Job 겹침 방지(H10). 단위 49개, 정상 종료 포함 e2e 338개 항목 통과.
- 2026-10-31: 4번 2단계 완료 — 연결 상태를 Redis `tc:plc-state:*` 로 분리, 어댑터 이벤트는 DB 상태를 바꾸지 않음, 기동은 의도(STATUS_1)만 봄, 커넥터 조회에 `connection`. 단위 51개, 정상 종료 포함 e2e 346개 항목 통과.
- 2026-10-31: 4번 3단계 완료 — Redis 임대 소유권 관리자(기본 꺼짐). 설계 §5.3 의 "주인만 끊김/Redis 전체 장애" 구분은 주인 안에서 불가능해 fail-static 으로 바로잡음(PLC 포트당 연결 1개가 마지막 장치). 해제 API 트랜잭션 누락(경합) 수정. 단위 54개, 켠·끈 상태 모두 정상 종료 포함 e2e 통과.
- 2026-10-31: 4번 4단계 완료 — 원격 어댑터와 내부 PLC 교환(HMAC·별도 포트). 로컬 두 대로 전달·서명·포트·이어받기 확인. 단위 59개, 켠·끈 상태 모두 정상 종료 포함 e2e 통과.
- 2026-10-31: 다중화 운영 절차(operations.md §4.2) — 준비·전환·순차 재기동·PLC 옮기기·확인·장애·비밀값 교체·되돌리기. 로컬 두 대로 순차 재기동(실패 0건/0.8초)·옮기기 확인. tc Redis 설정이 DB 번호를 받지 않아 MES 와 분리는 별도 인스턴스로.
