# tc e2e 시험

실제로 띄운 tc·vplc·MariaDB·Redis 를 대상으로 HTTP 로 시험한다. Mock 은 쓰지 않는다. 인프라 없이 도는 단위 테스트는 `./gradlew :tc:test`.

| 파일 | 내용 | 항목 |
|---|---|---|
| `setup_e2e.py` | vplc 를 바라보는 커넥터 `plc-e2e` 와 읽기 인터페이스를 만들고 게시(여러 번 돌려도 됨) | — |
| `test_plc_adapter.py` | 가짜 PLC 로 늦은 응답·연결 끊김·동시 요청·재게시 (docs/plc-connection-design.md §5) | 10 |
| `test_api_key.py` | API 키 인증·권한·사용량 제한·회전·만료·삭제 (docs/auth-and-audit-design.md §6) | 40 |
| `test_paths.py` | WRITE·콜백과 허용 목록·스케줄 Job·커넥터 해제 (docs/dispatch-redesign.md §4.1.2) | 17 |
| `test_config_history.py` | 설정 변경 이력 (§5) | 11 |
| `test_users.py` | 권한(§4)·사용자(§3)·로그인 실패 제한 | 54 |
| `test_shutdown.py` | 헬스 체크, 정상 종료 순서(웹 서버 → PLC 연결) — **tc 를 종료시킨다** | 5 |
| `fake_plc.py` | 시험용 가짜 MELSEC PLC(3E, 워드 읽기). n번째 요청에 값 n, 지연·끊기 지정 | |
| `ShutdownViaJmx.java` | JMX 로 Spring Boot 정상 종료 요청(Windows 에서 SIGTERM 대신) | |

## 준비

1. MariaDB(`tc` DB), Redis 를 띄운다. `tc/src/main/resources/application-local.yml` 에 `TC_DB_PASSWORD` 가 있어야 한다(시험이 DB 를 직접 확인한다).
2. vplc 를 띄우고, 127.0.0.1:15000 에 D 메모리를 가진 가상 PLC 를 등록·실행한다.
3. tc 를 **시험 설정**으로 띄운다.

   | 환경 변수 | 값 | 쓰는 시험 |
   |---|---|---|
   | `TC_DELIVERY_ALLOWEDURLPREFIXES_0` | `http://127.0.0.1:18090/` | test_paths(콜백 허용 목록) |
   | `PLC_REQUEST_TIMEOUT_MS` | `3000` | test_plc_adapter(늦은 응답) |
   | `TC_APICLIENT_EXPIRYCHECKCRON` | `*/30 * * * * *` | test_api_key(만료 알림) |
   | `SPRING_APPLICATION_ADMIN_ENABLED` | `true` | test_shutdown(JMX 정상 종료) |

4. 시험 관리자 계정을 알려 준다: `TC_E2E_ADMIN_ID`, `TC_E2E_ADMIN_PASSWORD`(또는 `TC_E2E_ADMIN_FILE` — `id=…`, `password=…` 두 줄). 기본 아이디는 `tc-tester`.
5. 정상 종료 시험을 돌리려면 `TC_E2E_TC_PID`(tc 프로세스 ID 또는 그 값이 든 파일), `TC_E2E_TC_LOG`(tc 표준 출력 로그)도 준다.

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
