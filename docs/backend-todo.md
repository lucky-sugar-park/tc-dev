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
| 11 | 결과 재전송 — 관리자가 감사 기록을 골라 수동으로(성공·실패 무관) | ✅ 백엔드 | [redelivery-design.md](redelivery-design.md)(2026-10-31). 자동 재시도(outbox) 대신. 새 표 없이 송수신 감사로 — DELIVERY 감사에 보낸 응답 전체를 남긴다. `POST /api/v1/ifsvc/redelivery/{deliveries,responses}`, 관리자만. 기존 버그(`WebHookManager` NPE)도 고침. 화면은 5-5 송수신 기록(결과 재전송). 운영 반영은 migration·재기동 없음(배포 때 함께) |

## 장기 과제

| # | 일 | 상태 |
|---|---|---|
| 12 | PLC 보강: 주기적 연결 상태 확인, 4E 프레임, 응답 길이 검증, 포트 여러 개 병렬 시험 | ✅ 2026-10-31 — [plc-protocol-design.md](plc-protocol-design.md) §13. 실제 PLC 로 4E·루프백 확인은 남음 |
| 13 | Modbus·OPC UA 어댑터(tc, vplc) | 🔄 설계 — [plc-protocol-design.md](plc-protocol-design.md). 인터페이스를 MC 3E 프레임 바이트에서 논리 주소(점)로, 드라이버 경계, 다중화 계약을 작업 단위로. 결정 P1~P10 확정. **1~4단계 완료**(POINTS·MELSEC 드라이버 §12, MELSEC 보강 §13, Modbus TCP §14, 다중화 계약 `/execute`·RAW→POINTS 변환 §15). 5단계(새 프론트) 진행 중 — 설계 [frontend-design.md](frontend-design.md)(결정 F1~F10 확정), **5단계 완료 — 5-1 백엔드 계약·5-2 셸·5-3 PLC·5-4 전달·5-5 운영·5-6 관리·5-7 가상 PLC 화면** |
| 14 | 로컬 시험 데이터 정리(`plc-fake`, `e2e-*`·`hist-*`·`redis-down` 클라이언트, 시험 이력, 시험 DB `tc_boot`) | ⬜ |
| 15 | 비동기 어댑터 큐(죽은 코드) 제거 | ✅ | 어댑터의 타이머·큐·`request(holder, callback)`, `PlcReqAndResHolder`·`PlcRequestCallback`, 커넥터 설정 `asyncRequestUse`·`asyncTimer*`(API 필드 포함) 제거(2026-10-31). DB 칼럼은 남긴다 — `migration/007` 이 NULL 허용으로, DROP 은 주석 |
| 16 | 운영 환경의 실제 호출 시스템 API 키 전환(감사 `PRINCIPAL` 로 대상 확인 → 키 발급·교체 → 옛 계정 중지) | ⬜ 운영 작업 — 이 버전 배포 전 전환이 안 끝났으면 배포 직후 전환 스위치를 켠다 |
| 17 | 인터페이스 세부 정의 — [interface-detail-design.md](interface-detail-design.md) | 🔄 A(요청 미리보기)·B(인터페이스 settings) 완료. 남은 것: C 점 options(워드 안 비트 `D100.3`·`HR1.15` — I2 확정, 읽기 전용, [word-bit-points.md](word-bit-points.md), 문자열 인코딩, 배율·오프셋·단위 — I3), D OPC UA(Eclipse Milo Nexus 확인, 노드 찾아보기, 구독 결과를 전달 정의로 — I4) |
| 18 | vplc 단위 테스트(지금 0개) | ⬜ Modbus·OPC UA 가상 장비를 붙이기 전에 (tc 의 `ByteUtil` 은 RAW 와 함께 없앰) |

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
- 2026-10-31: v0.3.0 분기. 프론트 전에 PLC 일반화를 먼저 하기로(MELSEC 이 인터페이스 정의까지 묶여 있음). 설계 [plc-protocol-design.md](plc-protocol-design.md) 작성 — 결함 M1~M4(ASCII 프레임 오타, 명령 검사, 길이 수동 입력, 응답 길이 미검증) 포함.
- 2026-10-31: PLC 일반화 결정 P1~P10 제안대로 확정. 설정은 모두 application.yml 에 — 코드에만 있던 tc.delivery·tc.plc.ownership·tc.plc.state·tc.instance·tc.internal·plc.request.timeout-ms 를 기본값과 함께 적고, 쓰지 않던 oper.duplication.mode 를 지움.
- 2026-10-31: PLC 일반화 1단계 완료 — POINTS 인터페이스(점: 이름·주소·형), MELSEC 드라이버(3E BINARY·ASCII, 묶기, 비트 디바이스), 커넥터 protocol·settings, plcStatus, 값 기반 변경 감지, 결함 M1~M4, migration 008. 기존 결함 수정: 인터페이스 이름·일괄 삭제가 외래 키 오류로 500. 단위 75개, 정상 종료 포함 e2e·두 인스턴스 시험 통과. vplc 한계(만들지 않은 메모리에 무응답, 비트 쓰기·ASCII 데이터 미지원)는 2단계에서.
- 2026-10-31: PLC 일반화 2단계(#12) 완료 — 4E(일련번호, 시간 초과에 연결 유지), 주기적 루프백, 포트별 병렬 연결 시험(TCP+루프백), 응답 디코더 재작성. 결함 M5(tc ASCII 디코더), vplc V1~V3(이름 제어 500, 없는 메모리 무응답, 서브 명령·ASCII 데이터 무시) 고침, e2e fixture subCommand 0100→0000. e2e test_plc_mc.py(12).
- 2026-10-31: PLC 일반화 3단계 완료 — Modbus TCP 드라이버(HR·IR·CO·DI, FC 1·2·3·4·5·6·15·16, 워드 순서, 예외 코드 → plcStatus)와 어댑터. MELSEC 어댑터에서 TCP 공통 계층(TcpPlcAdapter·WireProtocol)을 떼어 냄 — 번호가 있는 요청(4E·Modbus)은 시간 초과에도 연결 유지. 커넥터 NULL 칼럼 조회 500 고침. 단위 ModbusDriverTest(13), e2e fake_modbus.py + test_plc_modbus.py(23).
- 2026-10-31: PLC 일반화 4단계 완료 — 인스턴스 사이 작업 단위 계약 `/internal/plc/{name}/execute`(점·값·설정 → 값·PLC 상태, RAW 는 프레임), 옛 `/exchange` 는 순차 재기동용으로 받고 보내는 쪽은 404 면 `/exchange` 로. RAW→POINTS 변환 API(`convert-to-points`, 미리보기 기본, 적용은 변경 이력, 헤더·데이터 정의는 남겨 되돌릴 수 있음). 단위 RawToPointsConverterTest(6), e2e test_plc_convert.py(14), test_plc_remote.py 에 /execute 6개.
- 2026-10-31: 5단계(새 프론트) 설계 frontend-design.md — 독립 앱 + MES framework·shared, 정적 메뉴, 화면 19개, 다국어 4개 언어(F5 수정 확정). 5-1 백엔드 계약 완료: 로그인 본문 토큰, 내 권한(화면 권한 uiroute.tc.*), 다국어·공통 코드·북마크 표와 API(migration 009, 씨앗), SSE(framework 컨트롤러 + 구독 권한, plc_connection 이벤트), 홈 요약, 시험 읽기, 프로토콜 설명, 화면 서빙(TC_UI_DIR). e2e test_ui_api.py(31).
- 2026-10-31: 5-2 — wow-tc-frontend 프로젝트(MES framework·shared 0.8.46), 셸 TcLayout·tc 로그인·정적 메뉴(화면 19개, 권한으로 가림)·홈(실데이터), tc-config.json(vplc 메뉴), 화면 문구 씨앗 30-tc-screens.tsv. 개발 서버·tc 서빙(재기동 없이) 확인.
- 2026-10-31: 5-3 PLC 화면 — 커넥터(프로토콜별 설정 폼·게시/해제/멈춤·연결 시험·시험 읽기·연결 이력), 인터페이스(점 표 편집기·RAW 보기·POINTS 변환), 인터페이스 시험, 연결 모니터(SSE). 문구 31-tc-plc.tsv(4개 언어). Playwright e2e plc.spec.ts(6) 통과.
- 2026-10-31: 5-4 전달 화면 — 전달 정의(유형별 대상·보낼 때·바뀔 때만), WebHook(보내 보기), 외부 DB 연결(비밀번호 저장 여부·연결 시험). 문구 32-tc-delivery.tsv(4개 언어). Playwright delivery.spec.ts(2), 화면 시험 8개 통과.
- 2026-10-31: 5-5 운영 화면 — 송수신 기록(서버 조회 `audit/search/query`·거래 보기·결과 재전송), 메시지 모니터(SSE), 스케줄 Job(종류별 폼·멈춤/재개·실행 이력), 설정 변경 이력(앞뒤·바뀐 칸). 스케줄 버그 고침 — 멈춤·재개가 이름을 ID 로 넘겨 500, 간격 트리거가 6번 돌고 사라짐, 보관 기간 int 넘침. 문구 33-tc-ops.tsv. e2e test_ui_api 35·test_paths 19, Playwright 11개 통과. 운영 반영은 jar 교체(재기동 필요).
- 2026-10-31: 5-6 관리 화면 — 사용자(로그인 막힘 표시·제한 풀기·사용 이력·비밀번호 초기화), API 클라이언트(허용 opcode·IP·URL, 키 발급은 한 번만 보임·폐기), 운영 옵션(권한 검사 모드·전환 스위치), 다국어(4개 언어 표·빠진 것), 공통 코드(그룹·코드). 백엔드 `users/{id}/history`·`login-blocked`·`login-unblock`. 문구 34-tc-admin.tsv. e2e test_ui_api 38, Playwright 16개 통과. 운영 반영은 jar 교체(재기동 필요).
- 2026-10-31: 5-7 가상 PLC 화면 — 목록(등록·게시·시작·일시정지·정지·해제·연결), 메모리 보기·제어(10진·16진·비트·ASCII 표, 칸 고쳐 쓰기·비트 켜기·여러 값/문자열 쓰기·자동 새로 고침). vplc 고침: 워드 값 쓰기(words), 낮은 바이트 부호 번짐, 이름으로 읽기·쓰기·지우기 null, 게시 안 됨 NPE→409. 문구 35-tc-vplc.tsv. e2e test_vplc_http_methods 35, Playwright 18개 통과. 5단계(새 프론트) 완료.
- 2026-10-31: `run-backend.ps1` 기동 결과 표시 — 포트를 다른 프로세스가 쥐고 있으면 띄우지 않고 실패(pid·실행 명령), 띄운 java 가 포트를 쥔 뒤의 health 만 믿음, 실패 원인(APPLICATION FAILED TO START·ERROR·Caused by)을 보임. 다른 tc 가 포트를 쥐고 대신 UP 으로 답해 성공으로 잘못 보이던 것. `deploy-frontend.ps1` 이 robocopy 성공 코드를 종료 코드로 내던 것도 고침.
- 2026-10-31: 커넥터 화면 — 카드·표 보기, 발행 상태 칼럼, 작업 칼럼, 제목·건수, 쪽 넘김, 셀 편집(주소·포트·설명). 인터페이스 화면 — 필터 라벨 위로, 타입·응답 IF·작업, 송수신 이력 보기. Chrome 에서 확인(잘못된 포트 셀 편집이 화면을 죽이던 것 고침).
- 2026-10-31: 인터페이스 세부 정의 설계 [interface-detail-design.md](interface-detail-design.md)(결정 I1~I6, I3 배율은 tc·I4 구독은 전달 정의 경로). A단계 — `PlcDriver.plan`(실행과 같은 묶기·프레임), `POST /ifsvc/management/plan`, 상세 보기·요청 미리보기. 로컬 재기동 뒤 화면에서 미리보기 확인.
- 2026-10-31: B단계 — 인터페이스 settings(MELSEC 중계·감시 타이머, Modbus unitId·writeFunction, mergeGap), migration 010, 폼 탭, 변환기가 다른 경로를 settings 로 옮김. 단위 107개 통과. e2e·Playwright 는 시험 계정이 없어 아직 돌리지 않음(항목은 더함). 운영 반영 대기: migration 006~010·`TC_SECRET_KEY`·재기동, #16 API 키 전환.
- 2026-11-01: RAW 인터페이스 제거(결정 I7, [interfaces.md](interfaces.md)) — 인터페이스는 점으로만 정의, 타입은 READ·WRITE. `header_prop`·`data_prop` 표와 RAW 클래스·API(변환·헤더 템플릿)·어댑터의 RAW 요청 경로·원격 `/exchange` 를 없앰. 전달 정의가 없는 인터페이스는 callback 만(응답 인터페이스 WebHook 경로 없음). migration 011(RAW 인터페이스·옛 칼럼·화면 문구 삭제). 단위 94개 통과, e2e 는 아직 안 돌림.
- 2026-11-01: RAW 뒷정리 — 인터페이스의 `sync`·`reply`·`webHookNameList`(읽는 곳 없음)와 `PlcInterfaceCommand` 의 교환 전·후 훅(고를 하위 클래스가 없음)을 없앰. migration 012(칼럼 DROP, 없는 인터페이스를 가리키는 스케줄 Job 확인 SELECT). 결정 I5 를 I7 로 대체 표시.
- 2026-11-01: 회귀 시험 — 백엔드 e2e 451개 항목 통과(소유권·두 인스턴스·M 메모리·화면 서빙은 건너뜀), Playwright 20개 통과. 시험 고침: `test_users` 마지막 관리자 항목은 다른 활성 관리자가 있으면 건너뜀(로컬에 사람 계정이 있으면 시험 관리자의 역할을 실제로 떼어 이후가 모두 403 이 되던 것), `test_plc_adapter` 가 `plc.fake.read` 를 커넥터와 따로 확인해 만듦, `test_config_history` 가 없어진 `.reply` 대신 `plc.e2e.d100.read`, `test_plc_points` 의 `endsWith` 오타. 시험 계정 비밀번호 파일은 `run\local\e2e-admin.txt`·`ui-e2e-password.txt`(git 밖).
