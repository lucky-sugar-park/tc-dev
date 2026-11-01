# tc e2e 시험

실제로 띄운 tc·vplc·MariaDB·Redis 를 대상으로 HTTP 로 시험한다. Mock 은 쓰지 않는다. 인프라 없이 도는 단위 테스트는 `./gradlew :tc:test`.

| 파일 | 내용 | 항목 |
|---|---|---|
| `setup_e2e.py` | vplc 를 바라보는 커넥터 `plc-e2e` 와 읽기 인터페이스(`plc.e2e.d100.read` — D100·D101 점)를 만들고 게시(여러 번 돌려도 됨). 옛 RAW 정의가 DB 에 남아 있으면 `migration/011_remove_raw.sql` 을 먼저 돌린다 | — |
| `test_plc_adapter.py` | 가짜 PLC 로 늦은 응답·연결 끊김·동시 요청·재게시, 끊김 뒤 연결 상태의 마지막 오류 (docs/plc-connection-design.md §5) | 11 |
| `test_api_key.py` | API 키 인증·권한·사용량 제한·회전·만료·삭제 (docs/auth-and-audit-design.md §6) | 40 |
| `test_paths.py` | WRITE·콜백과 허용 목록·커넥터 해제 (docs/dispatch-redesign.md §4.1.2) | 10 |
| `test_schedule_jobs.py` | 스케줄 Job(docs/schedule-jobs.md) — 종류 API, 저장 검사(클래스·인터페이스·점·값 형·주기), 성공·실패 이력과 비고(거래 ID → 송수신 기록), 이력 검색(결과·기간·쪽), WRITE Job 이 쓴 값, 정리 Job, 목록의 최근 실행·다음 실행·최근 실패, 속성 통째로 바꾸기, 멈춤·재개·삭제. 약 40초 | 33 |
| `test_config_history.py` | 설정 변경 이력 (§5) | 11 |
| `test_users.py` | 권한(§4)·사용자(§3)·로그인 실패 제한·전환 스위치 | 61 |
| `test_framework_046.py` | framework 0.8.46 반영 — 공개 경로 설정, 검색 조건(conjType 없음 AND, 없는 항목 400) | 8 |
| `test_http_methods.py` | 상태를 바꾸는 API 의 메서드(#9) — 예전 GET 은 405, 삭제는 DELETE·제어·사용 변경은 POST 로 처리, 토큰 없으면 401. 없는 대상은 404(`ERR_404`) — 500 이 나오면 실패 | 71 |
| `test_db_table.py` | DB_TABLE 결과 전달(#10) — 외부 DB 연결 등록(암호화·금지 옵션·예약 이름), 테이블 검증, 성공·실패 행 기록, 사용 중지, 삭제 보호. **`TC_SECRET_KEY` 필요** | 33 |
| `test_redelivery.py` | 관리자 결과 재전송(#11) — 전달 기록 재전송(꺼진 연결 거절·켠 뒤 성공·중복 클릭·유니크 키 "이미 있음"·본문 없는 옛 기록), 응답·오류 기록을 고른 대상으로(전달 조건 무시, 재전송 헤더), 잘못된 요청, 지워진 WebHook, 응답 기록 → callback(원래 요청의 `pushUrl`, 동기 요청은 주소 없음). **`TC_SECRET_KEY`·18090 허용 필요** | 45 |
| `test_vplc_http_methods.py` | vplc 의 상태를 바꾸는 API 메서드 — 예전 GET 은 405, 삭제는 DELETE·게시·해제·제어는 POST, 없는 가상 PLC 는 404(`ERR_404`), 메모리 능력 API(/memory — 영역 읽기·쓰기·범위·종류 맞지 않는 API 400·없는 PLC 404), 상태 규칙(실행 중 시작·재개 409), 메모리 워드 값 쓰기·이름으로 읽기·게시 안 됨 409. vplc 주소는 `TC_E2E_VPLC_URL` | 43 |
| `test_webhook_cache.py` | WebHook 캐시(H7) — 다른 인스턴스가 바꾼 주소·지운 WebHook 을 DB 를 직접 고쳐 흉내 내고, 만료(10초) 뒤 따라오는지. 약 25초 걸린다. **18090 허용 필요** | 6 |
| `test_plc_state.py` | PLC 연결 상태 분리(다중화 2단계) — 조회 응답의 `connection`(Redis), 상태 키 만료·5초 갱신, 해제·게시 때 의도(DB)와 연결 상태(Redis), 이벤트가 DB 상태를 고치지 않음 | 7 |
| `test_plc_ownership.py` | PLC 소유권(다중화 3단계) — 임대·연장·키 사라지면 다시 잡기·남의 임대면 강등·이어받기·해제/게시·균형 규칙. **tc 를 `TC_PLC_OWNERSHIP_ENABLED=true` 로 띄웠을 때만**(아니면 건너뜀) | 17 |
| `test_plc_remote.py` | 두 인스턴스 PLC 교환 전달(다중화 4단계) — 주인이 아닌 인스턴스로 온 요청(기존·새 인터페이스), 감사 한 번, 내부 엔드포인트 서명·포트, 작업 단위 `/execute`(READ·오류), 서명 시험은 `TC_E2E_INTERNAL_SECRET_FILE` 이 있을 때만, `TC_E2E_FAILOVER=graceful\|kill` 이면 이어받기 시간. **두 번째 인스턴스(`TC_E2E_BASE_URL_B`)가 있을 때만**(아니면 건너뜀) | 13(+2~3) |
| `test_plc_points.py` | 점 인터페이스(PLC 일반화 1단계) — 같은 주소를 읽는 인터페이스와 같은 값, 여러 형 쓰기·readBack, 값 기반 변경 감지, plcStatus, 등록 검증(주소·형·커넥터·plcName·points·형식)·쓰기 값 검증, 커넥터 설정 정규화, 요청 미리보기(`/plan` — READ·WRITE·없는 커넥터·잘못된 주소), 인터페이스 settings(덮어쓰기·저장·거절), 인터페이스 삭제. 비트 디바이스는 vplc 에 M 메모리가 있을 때만 | 33(+1) |
| `test_plc_mc.py` | MELSEC 보강(PLC 일반화 2단계) — vplc 오류 종료 코드(없는 메모리), 포트별 연결 시험(TCP+루프백), 4E 프레임(두 인터페이스 같은 값, 시간 초과에 연결 유지), 주기적 루프백(멈춘 PLC 연결 닫고 다시 잇기). **vplc 를 잠시 멈추고 plc-e2e 설정을 바꿨다가 되돌린다** | 12 |
| `test_plc_modbus.py` | Modbus TCP(PLC 일반화 3단계) — 가짜 장치(:15200)를 띄워 커넥터 자동 설정·연결 시험, 점 없음 거절, 점 검증, HR·IR·DI 읽기·HR·CO 쓰기(readBack), 워드 순서 LITTLE, 예외 응답 → plcStatus, 시간 초과에 연결 유지, 쉬는 연결 확인으로 닫고 다시 잇기. 커넥터 `plc-modbus-e2e` 를 만들고 지운다 | 23 |
| `test_plc_vmodbus.py` | vplc 의 가상 Modbus PLC(`mb-e2e`·`mb-e2e-2`, 없으면 등록·실행) — 게이트웨이 한 포트 뒤 단위 ID 1·2(인터페이스 settings `unitId`), 없는 단위 ID → 예외 0x0B, 레지스터 안 비트·배율 읽기, 배율 쓰기 → 장치에 원시 값. **vplc 가 없으면 건너뜀** | 8 |
| `test_plc_opcua.py` | OPC UA(D단계) — vplc 의 가상 OPC UA 서버(`ua-e2e`, 없으면 태그와 함께 등록·실행) 상대로 커넥터 등록·연결 시험·게시, 노드 찾아보기, 읽기·쓰기(readBack)·배율, 없는 노드 plcStatus, 범위 밖 400, 미리보기, SUBSCRIBE 구독 알림 감사, D2(Basic256Sha256·사용자 이름·nsu=·틀린 비밀번호·인증서 내려받기·잊기). **vplc 가 없으면 건너뜀** — 구독 확인에 최대 30초 | 30 |
| `test_ui_api.py` | 새 화면 백엔드 계약(5단계 5-1) — 로그인 본문 토큰·재발급, 내 권한(역할별·화면 권한), 셸 빈 응답, 다국어(공개·4개 언어·관리), 공통 코드, 북마크, SSE(공개 연결·구독 권한·audit_message 수신), 홈 요약, 감사 조회(기간·실패만·쪽), 사용 이력·로그인 제한 풀기, 시험 읽기, 프로토콜 설명, 화면 서빙(`TC_E2E_UI_DIR` 이 tc 의 `TC_UI_DIR` 과 같을 때만). 시험 사용자를 만들고 지운다 | 38 |
| `test_shutdown.py` | 헬스 체크, 정상 종료 순서(웹 서버 → PLC 연결) — **tc 를 종료시킨다** | 5 |
| `fake_plc.py` | 시험용 가짜 MELSEC PLC(3E, 워드 읽기). n번째 요청에 값 n, 지연·끊기 지정 | |
| `fake_modbus.py` | 시험용 가짜 Modbus TCP 장치(FC 1·2·3·4·5·6·15·16, 처음 값, 답하지 않는 주소 HR500, HR990 에 n 을 쓰면 n 초 동안 무응답) | |
| `ShutdownViaJmx.java` | JMX 로 Spring Boot 정상 종료 요청(Windows 에서 SIGTERM 대신) | |

## 준비

1. MariaDB(`tc` DB), Redis 를 띄운다. `tc/src/main/resources/application-local.yml` 에 `TC_DB_PASSWORD` 가 있어야 한다(시험이 DB 를 직접 확인한다).
2. vplc 를 띄우고, 127.0.0.1:15000 에 D 메모리를 가진 가상 PLC 를 등록·실행한다.
3. tc 를 **시험 설정**으로 띄운다.

   | 환경 변수 | 값 | 쓰는 시험 |
   |---|---|---|
   | `TC_DELIVERY_ALLOWED_URL_PREFIXES` | `http://127.0.0.1:18090/` | test_paths(콜백 허용 목록) |
   | `PLC_REQUEST_TIMEOUT_MS` | `3000` | test_plc_adapter(늦은 응답) |
   | `TC_APICLIENT_EXPIRYCHECKCRON` | `*/30 * * * * *` | test_api_key(만료 알림) |
   | `SPRING_APPLICATION_ADMIN_ENABLED` | `true` | test_shutdown(JMX 정상 종료) |
   | `TC_SECRET_KEY` | Base64 32바이트(예: `python -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())"`). 재기동해도 같은 값을 쓴다 | test_db_table(DB 연결 비밀번호 암호화) |

4. 시험 관리자 계정을 알려 준다: `TC_E2E_ADMIN_ID`, `TC_E2E_ADMIN_PASSWORD`(또는 `TC_E2E_ADMIN_FILE` — `id=…`, `password=…` 두 줄). 기본 아이디는 `tc-tester`.
5. `TC_E2E_TC_LOG`(tc 표준 출력 로그)를 주면 API 키 만료 알림 로그까지 확인한다(없으면 그 항목만 건너뜀). 정상 종료 시험은 `TC_E2E_TC_PID`(tc 프로세스 ID 또는 그 값이 든 파일)와 `TC_E2E_TC_LOG` 가 모두 필요하다.

다른 경로는 `common.py` 머리말의 환경 변수로 바꾼다(`TC_E2E_BASE_URL`, `TC_E2E_MYSQL`, `TC_E2E_REDIS_CLI` 등).

## 실행

```bash
cd wow-tc-backend/e2e
python -X utf8 run_all.py
```

`--shutdown` 을 붙이면 마지막에 tc 를 정상 종료시키는 시험까지 돈다.

## 주의

- 시험은 로컬 DB 에 데이터를 남긴다(`e2e-*`·`hist-*` API 클라이언트, `plc-fake` 커넥터·인터페이스, 이력). 시험 사용자는 끝에 지운다.
- 로그인 실패 제한 시험은 `127.0.0.1` 의 실패 집계를 Redis 에서 지운다.
- `test_users.py` 는 "마지막 활성 관리자" 보호를 시험하므로, 시험 관리자 외에 **다른 활성 관리자가 없어야** 한다.
