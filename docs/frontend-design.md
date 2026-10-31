# tc 프론트 재작성 설계 (PLC 일반화 5단계)

작성 2026-10-31. 관련: [plc-protocol-design.md](plc-protocol-design.md) §11 5단계, [auth-and-audit-design.md](auth-and-audit-design.md) §4(권한), 옛 화면 목록은 §9.

## 1. 정한 것 (2026-10-31, 사용자)

| # | 내용 |
|---|---|
| U1 | **독립 앱**(`wow-tc-frontend`). mes-dev 와 같은 스택·구조(React 19·TS·Vite 6·MUI 9·DataGrid Pro·Zustand·Axios, Package-by-Feature)이고, **MES 의 `wow-mes-frontend-framework`·`wow-mes-frontend-shared` 를 가져다 쓴다.** MES 의 화면 경로·권한·환경설정 API 체계에 맞출 필요는 없다 |
| U2 | **메뉴는 정적으로 고정**(코드에 둔다) — MES 처럼 DB(`/api/menus`·`/api/ui-routes`)에서 받지 않는다 |
| U3 | 화면은 옛 React 프로젝트(`D:\Source\Workspaces\tc\tc-frontend`)의 메뉴·화면을 참고하되 개선·보완·추가, **mes-dev 의 Look & Feel** 유지. 가상 PLC(vplc) 시험 화면 포함 |
| U4 | **다국어·공통 코드(LOV)·북마크·설정은 mes-dev 것을 참고**(같은 계약) |
| U5 | 1차 범위: PLC 커넥터·인터페이스, 전달·WebHook·외부 DB, 운영 기록, 사용자·권한·API 키 |
| U6 | 배포: **tc 가 jar 밖 폴더를 서빙**(화면 배포에 재기동 없음) |

## 2. 조사한 것

### 2.1 MES framework·shared 를 독립 앱에서 쓰기

패키지를 고치지 않고 된다.

- 두 패키지는 **TS 소스로 배포**(`exports: ./src/index.ts`)되고 Vite 가 그대로 컴파일한다. Nexus group 저장소(`.npmrc`)에서 `0.8.46` 을 받는다.
- **정적 메뉴·경로**: `AppRouter`·`ScreenHost` 는 `uiRoutes` 가 비면 `ScreenDef.path` 로 경로를 만들고, `DefaultLayout` 은 `/api/menus` 가 비거나 실패하면 `config.menu` 를 쓴다 — 이미 있는 대체 경로다.
- **꼭 있어야 하는 백엔드 API는 둘**: `POST /api/auth/login`(본문에 `accessToken`), `GET /api/me/permissions`(없으면 메뉴가 비고 화면마다 "권한 없음"). 나머지는 실패해도 조용히 넘어가거나 사용자가 누를 때만 부른다.
- 걸리는 것
  - `DefaultLayout` 은 늘 SSE(`/api/sse/connect/{clientId}`)를 열고, 없으면 10초 뒤 "서버와 실시간 연결이 끊겼습니다" 띠가 뜬다.
  - 머리말·로그인 화면의 제목이 `WOW-MES` 로 박혀 있다(`RichHeader` 는 `title` 을 받지만 `DefaultLayout` 이 넘기지 않는다).
  - `shared` 가 `xlsx` 를 `file:../vendor/xlsx-0.20.3.tgz` 로 적어 모노레포 밖에서 설치가 안 된다. `@mui/x-data-grid-pro` 를 선언하지 않고 쓴다(앱이 직접 설치).
  - localStorage 키가 `wow-mes-*` — tc 는 다른 출처(포트)에서 열리므로 겹치지 않는다.
- 목록 화면 틀: `PageContainer` → `CommandBarActions`(`ScreenAction` refresh·search·add·excelDown) → `FilterBar` → `DataGridPro`(전역 테마에 표 스타일), 편집은 `DraggablePaper`·`DialogTitleClose`·`DialogContent dividers`·`DialogFooter`. 화면은 제목을 그리지 않는다(빵 부스러기 끝이 제목). MDM 의 `MasterToolbar`·`StandardMasterGrid` 는 내보내지 않아 tc 에 옮겨 둔다.

### 2.2 다국어·LOV·북마크·설정 (mes-dev 실제 동작)

| 기능 | 백엔드 | 프론트 | 없으면 |
|---|---|---|---|
| 다국어 | 표 `I18N_ITEM`(CODE·LANG_CODE 키, VALUE, KIND, IS_USE), `GET /api/i18n/messages?lang=KO` → `{코드: 값}` (**로그인 전에도**) | `useI18n().t(key, 기본값)`, 메뉴는 `i18nCode`, 언어 KO·EN·JA·ZH | 한국어 기본값으로 보인다 |
| LOV | 표 `COMMON_CODE`(CODE 키, NAME, I18N_CODE, PARENT, IS_USE), `GET /api/common-codes/lov?parent=&leaf=` | `useLov`·`LovSelect`·`useLovLabeler` | 목록이 빈다 |
| 북마크 | 표 `BOOKMARK`(USER_ID·NAME 키, PATH_NAME, DESCRIPTION, STATE_JSON), `GET·POST /api/me/bookmarks`, `PUT·DELETE ?name=` | 머리말 북마크 메뉴·화면 상태 복원 | 빈 목록, 추가 때 오류 알림 |
| 설정 | **백엔드 없음** — 테마·언어·탭 설정 모두 localStorage | `SettingsDialog` | — |

MES 문서(CLAUDE.md)가 말하는 IndexedDB 캐시·버전 확인은 실제로는 없다(메모리·localStorage 뿐).

### 2.3 옛 tc 화면

메뉴 항목 대부분이 화면이 없어 404, 대시보드는 템플릿 가짜 숫자, 실시간 메시지 모니터는 SSE 주소 버그로 동작하지 않았다. 고칠 것과 살릴 것은 §5 화면 표의 "옛 화면" 칸.

## 3. 구조

```
wow-tc-frontend/                     # npm 프로젝트 하나(앱 하나 — 모노레포 아님)
├── .npmrc                           # Nexus group 저장소
├── package.json                     # wow-mes-frontend-framework·shared 0.8.46, @mui/x-data-grid-pro, react 19 …
├── vite.config.ts                   # dev: /api → tc(8087), /vplc-api → vplc(8088)
├── public/tc-config.json            # 배포 때 바꾸는 값(vplc 주소 등) — 빌드 없이
└── src/
    ├── main.tsx                     # 라이선스, connectFrameworkI18n(), bootstrap(config)
    ├── config.tsx                   # screens(ScreenDef[], path 고정) + menu(MenuNode[], 정적) + layout
    ├── layout/TcLayout.tsx          # DefaultLayout 을 바탕으로 제목 "WOW-TC"
    ├── shared/                      # MasterToolbar·StandardMasterGrid(옮김), api 도우미, 공통 형
    ├── home/                        # 대시보드
    ├── plc-connector/  interface/  plc-monitor/
    ├── delivery/  webhook/  db-connection/
    ├── audit/  message-monitor/  schedule-job/  config-history/
    ├── user/  api-client/  i18n-admin/  common-code-admin/  ops-option/
    └── vplc/                        # 가상 PLC 목록·메모리 보기·제어
```

- 기능 폴더마다 `pages/`·`components/`·`services/`(apiClient 호출)·`types/`.
- 화면 키·경로·권한 코드: 화면 키 `tc.plc.connector` → 경로 `/plc/connectors` → 권한 코드 `uiroute.tc.plc.connector`(framework 의 `resourceCodeOf`).
- 메뉴 이름은 `i18nCode`(`i18n.menu.tc.…`)와 한국어 기본값을 함께 둔다 — 다국어 행이 없어도 한국어로 보인다.

## 4. 백엔드에 더할 것

### 4.1 shared 가 부르는 계약 (경로가 `/api/v1` 이 아니라 `/api` — MES 와 같게)

| API | 내용 |
|---|---|
| `POST /api/auth/login`·`/reissue` | 응답 본문에 `accessToken` 을 더한다(지금은 `Authorization` 헤더에만). `userInfo` 를 MES `BackendUserInfo` 모양(`id, name, email, enabled, userRoles, passwordChangeRequired`)으로 맞춘다. 헤더는 그대로 — 기존 호출자 호환 |
| `GET /api/me/permissions` | 로그인 사용자의 `{자원 코드: 동작}` — `TcPermissionChecker` 의 역할 표 그대로(`tc.plc: "ACRUDX"` …) + **화면 권한 `uiroute.tc.*`**(화면 → 자원 표, §5). 관리자는 `{"*": "ACRUDX"}` |
| `GET /api/i18n/messages?lang=` | 공개. 표 `I18N_ITEM` |
| `GET /api/common-codes/lov?parent=&leaf=` | 로그인. 표 `COMMON_CODE` |
| `/api/me/bookmarks` | GET·POST, PUT·DELETE `?name=`. 표 `BOOKMARK`. 중복 409 `ERR_409_EXISTS` |
| `GET /api/sse/connect/{clientId}`, `POST /api/sse/event/add/{clientId}` | shared `serverEvents` 계약. 이벤트: `notification`(shared 알림), **`plc_connection`**(연결 상태 바뀜), **`audit_message`**(메시지 모니터, 구독한 클라이언트에만). 지금의 `/connect/{userId}`(옛 화면용)는 지운다 |
| `GET /api/menus`·`/api/ui-routes` → `[]`, `GET /api/operation-options/effective` → `{}` | 정적 메뉴를 쓰게 하는 빈 응답(실패시켜도 되지만 콘솔 오류·잠금 해제를 피한다) |
| `POST /api/me/password` `{currentPassword,newPassword}`, `GET /api/me/profile` | shared 머리말 메뉴용 — 지금의 `/api/v1/me` 를 감싼다 |

### 4.2 화면용 관리 API

| API | 내용 |
|---|---|
| `GET /api/v1/plc-connector/protocols` | **만들었다** — 프로토콜별 설정 항목(형·기본값·범위·선택지·설명)·주소 영역(비트·16진·읽기 전용·쓸 수 있는 형)·주소 예. 커넥터 설정 폼과 점 주소 도움말을 서버 정의로 그린다 |
| `POST /api/v1/plc-connector/test-read/byName/{name}` | **만들었다** — 인터페이스 없이 점을 읽어 본다(게시된 커넥터, 다중화면 주인으로). `{values, plcStatus, elapsedMs}` |
| 다국어·공통 코드 관리 | `I18N_ITEM`·`COMMON_CODE` 조회·등록·수정·삭제(관리자). 화면에서 메뉴 이름·LOV 를 고친다 |
| 대시보드 요약 | `GET /api/v1/dashboard/summary` — 커넥터 연결 상태 건수, 오늘 요청 건수·실패 건수(감사), 최근 오류 10건, 인스턴스(다중화) |

### 4.3 화면 서빙 (U6)

- 설정 `tc.ui.dir: ${TC_UI_DIR:}` — 비우면 서빙하지 않는다(지금처럼). 값이 있으면 그 폴더를 `/` 에서 서빙하고, `/api/**`·`/internal/**`·`/actuator/**` 가 아닌 모르는 경로는 `index.html`(SPA). 캐시: `index.html` 은 no-cache, 해시 붙은 자산은 길게.
- 배포: `vite build` → 폴더에 복사(robocopy `/MIR`). **tc 재기동 없음.**
- 다중화: 인스턴스마다 같은 폴더(공유 디스크 또는 각자 복사).

### 4.4 DB (migration 009)

`I18N_ITEM`, `COMMON_CODE`, `BOOKMARK` — mes-dev 와 같은 칼럼. 씨앗: shared 셸 문구(mes `i18n/screen/10-shared.tsv`)·tc 메뉴·화면 문구(KO·EN), LOV(`common.code.ui.tab_user_pref`·`tab_retention` + tc 코드: 프로토콜·프레임·인터페이스 형·점 형·커넥터 상태 …). 이미 있는 행은 덮지 않는다(mes 의 `ScreenI18nSeed` 와 같은 규칙).

## 5. 메뉴와 화면 (정적)

| 메뉴 | 화면 키 · 경로 | 권한 자원 | 내용 | 옛 화면 |
|---|---|---|---|---|
| **홈** | `tc.home` `/` | — | 커넥터 상태(연결·끊김·멈춤), 오늘 요청·실패 건수, 최근 오류, 인스턴스(다중화면 PLC 주인) | 템플릿 가짜 숫자 → 실데이터 |
| **PLC** › 커넥터 | `tc.plc.connector` `/plc/connectors` | `tc.plc` | 목록(이름·프로토콜·호스트·포트·연결 상태·주인·마지막 오류), 등록·수정(프로토콜을 고르면 설정 폼이 `/protocols` 로 바뀜, 어댑터 클래스·헤더 템플릿 입력 없앰), 게시·해제·멈춤·재개, 포트별 연결 시험, **시험 읽기**(점 몇 개를 적고 바로 읽기), 연결 이력 | 클래스 이름·비동기 옵션 입력, 시작포트+포트수 → 포트 목록 |
| PLC › 인터페이스 | `tc.plc.interface` `/plc/interfaces` | `tc.interface` | 목록(이름·형·PLC·모드·사용), **POINTS 편집기**(점 표 — 이름·주소·형·개수·길이·순서 재정의, 주소 도움말, 행마다 시험 읽기), RAW 보기(헤더·데이터 속성, 읽기 전용) + **POINTS 로 변환**(미리보기·경고·적용), 전달 정의 연결, 사용 켜기·끄기 | 4단계 마법사·바이트 정의 → 점 표 |
| PLC › 인터페이스 시험 | `tc.plc.interface-test` `/plc/interface-test` | `action.tc.dispatch` | 인터페이스를 고르면 점 정의로 요청 `data` 양식을 만들어 줌, request·request-and-reply, 응답·plcStatus·걸린 시간 | JSON 직접 입력·검증 없음 |
| PLC › 연결 모니터 | `tc.plc.monitor` `/plc/monitor` | `tc.plc` | 모든 커넥터의 연결 상태 판(채널 수·주인 인스턴스·마지막 이벤트·오류), SSE `plc_connection` 으로 바로 갱신 | 없음(메뉴만) |
| **전달** › 전달 정의 | `tc.delivery.rule` `/delivery/rules` | `tc.delivery` | 인터페이스별 전달(REPLY·WEBHOOK·DB_TABLE·CALLBACK_URL, 조건) | 없음 |
| 전달 › WebHook | `tc.delivery.webhook` `/delivery/webhooks` | `tc.webhook` | 목록·등록·수정·시험(보낸 값·응답) | 행 안 편집 → 대화상자 |
| 전달 › 외부 DB 연결 | `tc.delivery.dbconn` `/delivery/db-connections` | `tc.dbconn` | 연결 등록(비밀번호 가림)·시험·테이블 검증 | 없음 |
| **운영** › 송수신 기록 | `tc.ops.audit` `/ops/audit` | `tc.audit` | 범위 조회(기간·opcode·성공 여부·트랜잭션 ID), 요청·응답·전달 묶어 보기, **결과 재전송**(관리자) | 무한 스크롤 표 유지 |
| 운영 › 메시지 모니터 | `tc.ops.monitor` `/ops/monitor` | `tc.audit` | 들어오는 요청·응답을 SSE 로(멈춤·필터·최대 행) | SSE 버그로 동작 안 함 |
| 운영 › 스케줄 Job | `tc.ops.schedule` `/ops/schedule-jobs` | `tc.schedule` | 목록·등록(Job 종류를 고름 — 클래스 이름 입력 없앰)·예약·취소·멈춤·재개, 실행 이력 | 클래스 이름 입력 |
| 운영 › 설정 변경 이력 | `tc.ops.confighist` `/ops/config-history` | `tc.confighist` | 대상·기간으로 조회, 앞뒤 비교 | 없음 |
| **관리** › 사용자 | `tc.admin.user` `/admin/users` | `tc.user` | 사용자·역할(ADMIN·OPERATOR·VIEWER)·잠금 해제·비밀번호 초기화, 로그인 이력 | 메뉴만 |
| 관리 › API 클라이언트 | `tc.admin.apiclient` `/admin/api-clients` | `tc.apiclient` | 클라이언트·허용 opcode·IP·URL 접두어, 키 발급·회전(키는 한 번만 보임)·폐기, 사용량 | 없음 |
| 관리 › 운영 옵션 | `tc.admin.option` `/admin/options` | `tc.option` | 재기동 없이 바꾸는 옵션 | 없음 |
| 관리 › 다국어 | `tc.admin.i18n` `/admin/i18n` | `tc.option` | 코드별 KO·EN·JA·ZH 값 | 없음 |
| 관리 › 공통 코드 | `tc.admin.code` `/admin/common-codes` | `tc.option` | 그룹·코드·이름 | 없음 |
| **가상 PLC** › 목록 | `tc.vplc.list` `/vplc/list` | `tc.plc` | 가상 PLC 등록·게시·시작·멈춤·연결된 클라이언트 | 유지·정리 |
| 가상 PLC › 메모리 | `tc.vplc.memory` `/vplc/memory` | `tc.plc` | 메모리 표(주소·비트·워드·ASCII), 값 쓰기·지우기, 시작·멈춤·멈춤(무응답)·재개, 자동 새로 고침 | 새로 고침 없음 |

- 머리말: 북마크(shared), 언어, 테마, 설정 대화상자(shared), 내 정보·비밀번호 변경·로그아웃.
- 가상 PLC 메뉴는 `tc-config.json` 에 vplc 주소가 있을 때만 보인다(운영 설치에는 vplc 가 없다).
- 화면 → 자원 표(§4.1 `uiroute.tc.*`)는 백엔드 한 곳(`UiRoutePermissions`)에 둔다. 화면을 볼 수 있으면 `A`, 그 자원의 동작(`C·R·U·D·X`)을 그대로 붙인다 — `ScreenAction` 이 버튼을 숨긴다.

## 6. 결정

**2026-10-31 확정 — F5 는 4개 언어(KO·EN·JA·ZH) 모두 채우는 것으로 바꾸고, 나머지는 아래 제안대로.**

| # | 질문 | 제안 | 이유 |
|---|---|---|---|
| F1 | 셸 | **`TcLayout`**: shared `DefaultLayout`(291줄)을 바탕으로 제목만 "WOW-TC"(로그인 화면 포함), 나머지(북마크·알림·SSE·탭·설정)는 그대로 | MES 패키지를 고치지 않는다. 다음 MES 회차에 `AppConfig.title` 이 생기면 `DefaultLayout` 로 바꾼다 |
| F2 | SSE | **tc 에 shared 계약대로 둔다**(§4.1) | 셸이 늘 연결하고, 연결 모니터·메시지 모니터가 쓴다. 지금의 옛 `/connect/{userId}` 는 지운다 |
| F3 | 권한 | **`/api/me/permissions` 를 tc 역할 표에서 만든다** + 화면 권한 `uiroute.tc.*` | 프론트에 권한 표를 두지 않는다 — 서버가 막는 것과 화면이 숨기는 것이 같은 표 |
| F4 | 로그인 응답 | **본문에 `accessToken` 을 더한다**(헤더도 남김) | shared 는 본문을 읽는다. 헤더를 읽는 기존 호출자(e2e·외부)는 그대로 |
| F5 | 다국어 범위 | **KO·EN·JA·ZH 모두 채운다**(확정 때 바꿈). tc 메뉴·화면 문구는 tc 씨앗 파일(4개 언어), shared 셸 문구는 mes 씨앗을 그대로. 관리 화면에서 고친다 | 사용자 결정 |
| F6 | xlsx | **tc 저장소 `vendor/` 에 같은 tgz 를 두고 `overrides`** | MES 패키지를 고치지 않고 설치된다. 엑셀 내려받기(목록 화면)는 쓴다 |
| F7 | MUI X 라이선스 | **MES 와 같은 키를 환경 변수(`VITE_MUI_LICENSE_KEY`)로** | DataGrid Pro 를 쓴다. 키를 저장소에 적지 않는다 |
| F8 | vplc 호출 | **화면이 vplc 를 직접 부른다**(`tc-config.json` 의 주소, vplc 에 CORS 허용) — dev 는 Vite 프록시 | tc 가 vplc 를 대신 부르면 tc 에 시험 전용 길이 생긴다. vplc 는 시험 장비라 운영 tc 와 떼어 둔다 |
| F9 | 운영 테스트 | **Playwright 로 화면 e2e**(로그인·커넥터 등록·시험 읽기·인터페이스 변환·vplc 메모리) — vitest 는 도우미 함수만 | 화면이 백엔드 계약을 바로 쓰므로 실제 tc·vplc 로 확인한다 |
| F10 | 순서 | **5-1 백엔드 계약(§4) → 5-2 셸·로그인·정적 메뉴·홈 → 5-3 PLC → 5-4 전달 → 5-5 운영 → 5-6 관리 → 5-7 가상 PLC** | 5-2 가 끝나면 화면을 하나씩 더해도 늘 돌아가는 앱이다 |

## 7. 남는 위험

- framework·shared 가 `0.8.46` 에 묶인다 — MES 가 올리면 tc 도 함께 올려 확인한다(peer: react 19.2, MUI 9.1, router 7).
- shared 의 MES 전용 도우미(`/attachments`·`/interlock` …)는 쓰지 않는다 — 쓰면 404.
- 화면 배포는 재기동이 없지만, §4 백엔드 계약은 **tc 재기동이 필요하다**(배포 때 함께).

## 8. 시험

- 백엔드: e2e `test_ui_api.py` — 로그인 본문 토큰, 권한 표(역할별), 다국어(로그인 전), LOV, 북마크(중복 409), SSE 연결·이벤트, 화면 서빙(SPA 대체·`/api` 는 그대로), 시험 읽기, 프로토콜 설명.
- 프론트: Playwright(F9), 타입 검사·빌드.

## 9. 옛 화면에서 버리는 것

가짜 대시보드, 화면 없는 메뉴(도움말·레이아웃 관리·분석), 하단 Admin 메뉴(연결 안 됨), 클래스 이름 입력(어댑터·커맨드·Job), 비동기 옵션, 시작포트+포트수(→ 포트 목록), 4단계 인터페이스 마법사(→ 점 표), 상태를 바꾸는 GET, 세션 저장소에 비밀번호.

## 10. 구현 — 5-1 백엔드 계약 (2026-10-31)

| 것 | 내용 |
|---|---|
| 로그인·재발급 | 본문 `accessToken`(헤더도 그대로), `userInfo.passwordChangeRequired`(= `passwordMustChange`) |
| `GET /api/me/permissions` | `TcPermissionChecker.effective(역할)` + `UiRoutePermissions`(화면 19개 → 자원). 관리자 `{"*":"ACRUDX"}` |
| `/api/me/profile`·`POST /api/me/password` | `/api/v1/me` 와 같은 일. 비밀번호 변경 필요 사용자도 쓴다(셸 기동용 경로는 변경 강제에서 뺐다 — 권한·다국어·셸 빈 응답·SSE) |
| 셸 빈 응답 | `GET /api/menus`·`/api/ui-routes` → `[]`, `GET /api/operation-options/effective` → `{}` |
| 다국어 | 표 `I18N_ITEM`, `GET /api/i18n/messages?lang=`(공개), 관리 `GET /api/i18n/items?q=&kind=`·`PUT·DELETE /api/i18n/items/{code}`(운영 옵션 권한, 변경 이력 I18N) |
| 공통 코드 | 표 `COMMON_CODE`, `GET /api/common-codes/lov?parent=&leaf=`(로그인), 관리 `GET·POST /api/common-codes`·`PUT·DELETE /{code}`(하위 있으면 409, 변경 이력 COMMON_CODE) |
| 북마크 | 표 `BOOKMARK`, `/api/me/bookmarks` GET·POST, `PUT·DELETE ?name=`(본인만, 중복 409 `ERR_409_EXISTS`) |
| 씨앗 | `ui-seed/i18n/10-shared.tsv`(mes-dev 셸 문구 245줄 그대로), `20-tc-menu.tsv`(tc 메뉴·앱 이름, 4개 언어), `ui-seed/codes.tsv`(셸 탭 설정·다국어 종류). 없는 줄만 넣는다 |
| SSE | framework `ServerSideEventController` 를 켰다(`/api/sse/connect/{clientId}` 공개, `event/add`·`remove` 로그인). 구독 권한 `TcSseSubscriptionAuthorizer` — `audit_message`(tc.audit R)·`plc_connection`(tc.plc R)·`apikey.expiring`(tc.apiclient R). 새 이벤트 `plc_connection`(연결 이벤트마다, PLC 를 연결한 인스턴스에서). 옛 `/connect/{userId}`·`/count` 는 지웠다 |
| 홈 요약 | `GET /api/v1/dashboard/summary` — 커넥터(전체·게시·연결·끊김·목록), 오늘 요청·실패(응답 설명이 `ERR_` 이거나 오류 행), 최근 오류 10건 |
| 화면 서빙 | `tc.ui.dir`(`TC_UI_DIR`) — `/` 는 index.html, 모르는 경로는 index.html(SPA), `/api`·`/internal`·문서·없는 자산은 404. `/assets/**` 1년 캐시, 나머지 no-cache. 폴더가 기동 뒤에 생겨도 된다 |
| 시험 읽기·프로토콜 설명 | §4.2 — `POST /api/v1/plc-connector/test-read/byName/{name}`(X 권한, 100점까지), `GET /api/v1/plc-connector/protocols` |
| DB | migration `009_ui_support.sql` |

- 함께 고친 것: 없음. 알게 된 것 — Spring Boot 4(Jackson 3)는 요청 JSON 에 빠진 **원시형**(boolean·int) 필드를 거부한다(400 "Failed to read request"). 새 요청 형은 `Boolean` 등 참조형으로 둔다.
- 시험: e2e `test_ui_api.py`(31) — 위 전부, 화면 서빙은 `TC_E2E_UI_DIR` 이 있을 때.

## 11. 구현 — 5-2 셸·로그인·정적 메뉴·홈 (2026-10-31)

| 것 | 내용 |
|---|---|
| 프로젝트 | `wow-tc-frontend`(npm 앱 하나, 0.3.0). React 19·Vite 6·MUI 9·DataGrid Pro 9, framework·shared `0.8.46`(Nexus group, `.npmrc`). xlsx 는 `vendor/xlsx-0.20.3.tgz` + `overrides`(F6). 개발 서버 5180 — `/api` → tc, `/vplc-api` → vplc |
| 셸 `TcLayout` | shared 에서 내보내는 부품만으로 `DefaultLayout` 을 다시 짰다 — 제목 WOW-TC, 로그인 `TcLoginPage`(회원가입·인증 메일·비밀번호 찾기 없음, 언어 선택), 비밀번호 변경 필요면 shared `ChangePasswordPage`. shared 가 내보내지 않는 탭 보존 정책·장식 없는 화면은 뺐다 — 탭은 화면 상태를 지킨다(`keep`) |
| 메뉴·경로 | `src/screens.ts`(화면 19개·그룹 5개, 정적). 잎마다 `routeCode: uiroute.<키>` — 셸이 서버 권한으로 가린다. 메뉴 이름은 `i18n.menu.<키>` |
| 배포 설정 | `/tc-config.json`(빌드 없이 고침) — `vplcUrl` 이 없으면 가상 PLC 메뉴를 숨긴다(F8) |
| 홈 | 커넥터(게시·연결·끊김), 오늘 요청·실패, 커넥터 연결 상태 표, 최근 오류 표. 보이는 동안 10초마다 |
| 나머지 화면 | "준비 중" — 메뉴·권한·경로는 열려 있고 5-3 부터 채운다 |
| 문구 | `ui-seed/i18n/30-tc-screens.tsv`(4개 언어) — 화면 코드 `t('i18n.tc.…', '한국어')` 와 같은 코드 |

확인: 타입 검사·빌드 통과, 개발 서버와 tc 서빙(`TC_UI_DIR` 에 `dist` 복사, 재기동 없음) 모두 로그인 화면·셸·메뉴 이동·홈 실데이터. 화면 e2e(F9 Playwright)는 화면이 생기는 5-3 부터.

## 12. 구현 — 5-3 PLC 화면 (2026-10-31)

| 화면 | 내용 |
|---|---|
| 커넥터 | 목록(프로토콜·주소:포트·프레임/장치·연결 상태·연결 인스턴스·마지막 오류), 상태는 SSE `plc_connection` + 10초 조회. 등록·수정 — 프로토콜을 고르면 설정 칸이 `/protocols` 로 바뀐다(빈 칸 = 서버 기본값, 16진 입력). 게시·해제·멈춤·재개(해제·멈춤은 확인), 포트별 연결 시험(결과 알림), **시험 읽기**(점 표 → 값·plcStatus·걸린 시간), 연결 이력(쪽 단위), 삭제, 엑셀 |
| 인터페이스 | 목록(형·정의·PLC 필터, REPLY 는 기본 숨김), 사용 켜고 끄기. POINTS 등록·수정 — **점 표 편집기**(주소를 적으면 영역에 맞는 형만, 읽기 전용 영역 경고, 순서 재정의, 위아래 이동). RAW 는 읽기 전용(헤더·데이터·응답 인터페이스) + **POINTS 변환 미리보기·적용**(경고·옛 응답 인터페이스 사용처). 엑셀 |
| 인터페이스 시험 | 인터페이스를 고르면 정의로 요청 data 양식(쓰기는 점마다 예시 값), JSON 검사, request-and-reply·request, 응답(HTTP·success·plcStatus·걸린 시간·data 표·JSON), 이번에 보낸 요청 20개 |
| 연결 모니터 | 커넥터 카드(연결 상태·주소·연결 인스턴스·마지막 이벤트·오류), 5초 조회 + SSE 이벤트 목록(이 화면을 연 뒤 200개) |

- 공통: `ListToolbar`(명령 줄 새로 고침·검색·추가·엑셀 + 필터 줄), `StandardGrid`(mes-dev MDM 표준 표), 행 버튼은 `usePerm`(화면 권한 C·U·D·X)으로 숨긴다.
- 문구: `ui-seed/i18n/31-tc-plc.tsv`(4개 언어, 105줄).
- 백엔드에서 알게 된 것: framework 검색 조건의 `pageInfo` 는 다섯 칸(`page`·`size`·`sort`·`sortDirection`·`sortBy`)을 모두 보내야 한다 — 하나라도 빠지면 400. 화면은 `pageCondition()` 으로 보낸다.
- 시험(F9): Playwright `e2e/plc.spec.ts`(6) — 홈, 커넥터(목록·프로토콜별 설정 폼·시험 읽기), 인터페이스(필터·RAW 보기·변환 미리보기, POINTS 등록·삭제), 인터페이스 시험(읽기 요청), 연결 모니터. 설치된 Chrome 으로(`channel: chrome`) — `npx playwright test`(README). 모두 통과.
- 고친 것(시험이 찾음): 시험 읽기 첫 점의 형을 영역 목록의 첫 것(MELSEC SM — 비트)으로 골라 워드 주소 D100 과 맞지 않던 것.
