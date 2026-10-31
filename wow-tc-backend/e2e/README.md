# tc e2e 시험

실제로 띄운 tc·vplc·MariaDB·Redis 를 대상으로 HTTP 로 시험한다. Mock 은 쓰지 않는다. 인프라 없이 도는 단위 테스트는 `./gradlew :tc:test`.

| 파일 | 내용 | 항목 |
|---|---|---|
| `setup_e2e.py` | vplc 를 바라보는 커넥터 `plc-e2e` 와 읽기 인터페이스를 만들고 게시(여러 번 돌려도 됨) | — |
| `test_plc_adapter.py` | 가짜 PLC 로 늦은 응답·연결 끊김·동시 요청·재게시 (docs/plc-connection-design.md §5) | 10 |
| `test_api_key.py` | API 키 인증·권한·사용량 제한·회전·만료·삭제 (docs/auth-and-audit-design.md §6) | 40 |
| `test_paths.py` | WRITE·콜백과 허용 목록·스케줄 Job·커넥터 해제 (docs/dispatch-redesign.md §4.1.2) | 17 |
| `test_config_history.py` | 설정 변경 이력 (§5) | 11 |
| `test_users.py` | 권한(§4)·사용자(§3)·로그인 실패 제한·전환 스위치 | 61 |
| `test_framework_046.py` | framework 0.8.46 반영 — 공개 경로 설정, 검색 조건(conjType 없음 AND, 없는 항목 400) | 8 |
| `test_http_methods.py` | 상태를 바꾸는 API 의 메서드(#9) — 예전 GET 은 405, 삭제는 DELETE·제어·사용 변경은 POST 로 처리, 토큰 없으면 401. 없는 대상은 404(`ERR_404`) — 500 이 나오면 실패 | 71 |
| `test_db_table.py` | DB_TABLE 결과 전달(#10) — 외부 DB 연결 등록(암호화·금지 옵션·예약 이름), 테이블 검증, 성공·실패 행 기록, 사용 중지, 삭제 보호. **`TC_SECRET_KEY` 필요** | 33 |
| `test_redelivery.py` | 관리자 결과 재전송(#11) — 전달 기록 재전송(꺼진 연결 거절·켠 뒤 성공·중복 클릭·유니크 키 "이미 있음"·본문 없는 옛 기록), 응답·오류 기록을 고른 대상으로(전달 조건 무시, 재전송 헤더), 잘못된 요청, 지워진 WebHook, 응답 기록 → callback(원래 요청의 `pushUrl`, 동기 요청은 주소 없음). **`TC_SECRET_KEY`·18090 허용 필요** | 45 |
| `test_vplc_http_methods.py` | vplc 의 상태를 바꾸는 API 메서드 — 예전 GET 은 405, 삭제는 DELETE·게시·해제·제어는 POST, 없는 가상 PLC 는 404(`ERR_404`). vplc 주소는 `TC_E2E_VPLC_URL` | 31 |
| `test_webhook_cache.py` | WebHook 캐시(H7) — 다른 인스턴스가 바꾼 주소·지운 WebHook 을 DB 를 직접 고쳐 흉내 내고, 만료(10초) 뒤 따라오는지. 약 25초 걸린다. **18090 허용 필요** | 6 |
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
