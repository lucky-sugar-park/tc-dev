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
- 배포: `vite build` → 폴더에 복사. **tc 재기동 없음.** 스크립트는 `scripts/deploy-frontend.ps1` — docs/operations.md §3.3
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

## 13. 구현 — 5-4 전달 화면 (2026-10-31)

| 화면 | 내용 |
|---|---|
| 전달 정의 | 목록(인터페이스·유형·대상·보낼 때·바뀔 때만·사용), 사용 켜고 끄기. 등록·수정 — 인터페이스(READ·WRITE) 고르기, 유형마다 대상(WEBHOOK → WebHook 목록, DB_TABLE → DB 연결 목록 + `tc` + 테이블, CALLBACK_URL → 대상 없음·허용 목록 안내), 보낼 때(늘·성공·실패), 값이 바뀌었을 때만, 정의 ID(비우면 서버가 만든다) |
| WebHook | 목록·등록·수정(대화상자 — 옛 표 안 편집 대신), URL·시험 데이터 JSON 검사, **보내 보기**(check-url 결과), 삭제 |
| 외부 DB 연결 | 목록(비밀번호는 저장 여부만), 등록·수정(비밀번호는 보내기만, 수정 때 비우면 그대로), 연결 시험(DB 제품·버전 또는 사유), 삭제 |

- 문구: `ui-seed/i18n/32-tc-delivery.tsv`(4개 언어).
- 시험: Playwright `e2e/delivery.spec.ts`(2) — WebHook 등록(URL 검사)·전달 정의 등록(실패일 때만)·사용 끄기·지우기, DB 연결 등록(비밀번호 저장됨)·연결 시험 실패 알림·지우기. 만든 것은 지운다. 전체 화면 시험 8개 통과.
- 시험 도우미: 열린 탭마다 검색칸이 있어 `searchFor()` 로 보이는 것만 쓴다.

## 14. 구현 — 5-5 운영 화면 (2026-10-31)

| 화면 | 내용 |
|---|---|
| 송수신 기록 | 기간(부터·까지)·opcode·거래 ID·종류·실패만으로 **서버에서** 찾는다(최신순, 쪽 단위). 행을 누르면 그 거래의 기록 전부(요청·응답·오류·전달). 관리자(`action.tc.redeliver` X)는 골라서 **결과 재전송** — 전달 기록은 같은 대상으로, 응답·오류 기록은 그 인터페이스의 지금 전달 정의 중에서 고른다 |
| 메시지 모니터 | SSE `audit_message` 로 들어오는 기록을 바로 보인다 — 멈춤·비우기·최대 행·걸러 보기, 받은 것·실패 수. 끊기면 다시 잇는다 |
| 스케줄 Job | 종류(PLC 요청·송수신 기록 정리·기타)를 고르면 필요한 값만 묻는다(옛 화면의 클래스 이름 입력 대신). cron 또는 시작 지연 + 반복 간격. 저장하면 사용일 때 (다시) 예약, 쓰지 않음이면 예약 취소. 멈춤·재개·예약 취소·다시 예약·실행 이력·삭제 |
| 설정 변경 이력 | 기간·대상 종류·대상 ID·바꾼 사람으로 찾고, 행을 누르면 앞뒤 값(JSON)과 바뀐 칸 |

- 백엔드: `POST /api/v1/ifsvc/audit/search/query`(`AuditQuery` — 기간·opcode·거래 ID·종류·실패만·호출자, 쪽 크기 1~500). framework 일반 검색은 날짜 범위를 문자열로 비교해 쓸 수 없어 JPA Specification 으로 따로. 실패만 = 응답 `ERR…` · 오류 · `OK` 로 시작하지 않는 전달.
- 고친 버그(스케줄): 멈춤·재개·상태 갱신이 Job **이름**을 ID 자리에 넘겨 500("No value present") → ID 로. 간격(simple) 트리거가 `withRepeatCount(5)` 라 6번 돌고 사라졌다(이후 멈춤은 "트리거 없음") → `repeatForever()`. `DataTableFlashJob` 보관 기간 계산의 int 넘침(`savingDays*24*60*60*1000`) → long.
- 문구: `ui-seed/i18n/33-tc-ops.tsv`(4개 언어). 앞 단계에서 빠진 공통 키(`i18n.auth.id`·`login`·`loginNeeded`·`password`, `i18n.common.saved`, `i18n.route.undefined`)를 `10-shared.tsv` 에 더했다 — 이제 화면이 쓰는 키는 모두 씨앗에 있다.
- 시험: e2e `test_ui_api.py` 35(감사 조회 4 더함), `test_paths.py` 19(스케줄 멈춤·재개 2 더함). Playwright `e2e/ops.spec.ts`(3) — 메시지 모니터(보낸 요청이 바로 보임)·송수신 기록(거래 ID 로 찾기)·스케줄 Job(등록·멈춤·실행 이력·삭제)과 설정 변경 이력(등록·삭제가 남음). 전체 화면 시험 11개 통과.
- 운영 반영: migration 없음. 백엔드 수정(감사 조회·스케줄 버그)은 jar 교체라 **tc 재기동이 필요**하다 — 배포 때 함께.

## 15. 구현 — 5-6 관리 화면 (2026-10-31)

| 화면 | 내용 |
|---|---|
| 사용자 | 목록(역할·사용 중지·**로그인 막힘**·비밀번호 바꿀 차례·마지막 로그인), 등록(초기 비밀번호 — 첫 로그인에서 바꾼다)·수정(이름·이메일·역할, 예전 역할은 보이기만), 사용 중지·재개, 비밀번호 초기화, **로그인 제한 풀기**, 사용 이력, 삭제 |
| API 클라이언트 | 목록(활성 키 수·가까운 만료 — 30일 안이면 붉게·마지막 사용), 등록·수정(허용 opcode — 인터페이스 목록에서·허용 IP/CIDR·결과 보낼 URL 접두어·초당/동시 한도), 사용 중지·재개, 삭제. **키** — 발급(수명 일수, 키는 그때 한 번만 보이고 복사)·폐기, 회전은 새 키 → 호출자 교체 → 옛 키 폐기(활성 키 둘까지). 새 클라이언트는 저장하면 바로 키 화면 |
| 운영 옵션 | 권한 검사 모드(AUDIT·ENFORCE), 기존 JWT 호출자 전환 스위치 — 재기동 없이, "설정값으로" 되돌리기, 바꾸면 설정 변경 이력. 다중화면 바꾼 인스턴스에만 |
| 다국어 | 코드별 KO·EN·JA·ZH 표(빈 칸 강조), 종류·빠진 것만 걸러 보기, 등록·수정·삭제 |
| 공통 코드 | 왼쪽 그룹·오른쪽 그 그룹의 코드, 등록(코드 앞부분을 그룹으로 채움)·수정·삭제(하위 코드가 있으면 서버가 거절) |

- 백엔드: `GET /api/v1/users/{id}/history?size=`(사용 이력 최신순 1~500), `GET /api/v1/users/login-blocked`(실패가 많아 잠시 막힌 아이디), `POST /api/v1/users/{id}/login-unblock`(아이디 제한과 실패 수를 지움, 이력 `LOGIN_UNBLOCK`). 계정 잠금은 여전히 없다 — IP 로 막힌 것은 풀지 않는다(auth-and-audit-design §3.4).
- 문구: `ui-seed/i18n/34-tc-admin.tsv`(4개 언어). 다국어 종류 이름은 공통 코드의 다국어(`i18n.common.code.i18n.kind.*`). 씨앗에 겹친 `i18n.common.saved`(10-shared 와 31-tc-plc)를 하나로.
- 시험: e2e `test_ui_api.py` 38(사용 이력·로그인 제한 3 더함 — 실제로 막히게 하지는 않는다: IP 실패 수가 남아 다른 시험의 로그인을 막을 수 있어서). Playwright `e2e/admin.spec.ts`(5) — 사용자(등록 검사·초기화·이력·중지·삭제), API 클라이언트(키 발급 한 번만 보임·폐기·삭제), 운영 옵션(보기만), 다국어(등록·수정·삭제), 공통 코드(그룹·코드, 하위 있으면 못 지움). 전체 화면 시험 16개 통과.
- 개발 서버: Windows 에서 의존성 재최적화가 `EPERM rename` 으로 막히면 서버를 멈추고 `node_modules/.vite` 를 지운 뒤 `npx vite optimize --force` 로 미리 묶고 다시 띄운다.
- 운영 반영: migration 없음. 백엔드(사용자 API 3개)는 jar 교체라 tc 재기동이 필요하다 — 배포 때 함께.
## 16. 구현 — 5-7 가상 PLC 화면 (2026-10-31)

| 화면 | 내용 |
|---|---|
| 가상 PLC 목록 | 목록(상태·프레임·IP·포트 범위·포트당 연결·연결 수·메모리 종류), 등록·수정(메모리 종류는 MC 코드에서 고름), 게시·해제, 시작·일시정지(연결은 두고 무응답 — tc 시간 초과 시험)·재개·정지, 포트별 연결(붙은 클라이언트), 메모리 화면으로, 삭제 |
| 메모리 보기·제어 | 가상 PLC(게시된 것)·메모리 종류·시작 주소·워드 수(20·50·100)·앞뒤 쪽, 자동 새로 고침(1·2·5초). 표 — 주소(W·B·X·Y 는 16진)·10진·부호 없음·16진·비트(눌러 켜고 끄기, 비트 디바이스는 칸마다 주소)·ASCII. 10진 칸을 고쳐 쓰기, **값 쓰기**(워드 값 여러 개·0x 16진, 또는 문자열), 종류 전체 지우기, 시작·일시정지·재개·정지 |

- 화면이 vplc 를 바로 부른다(F8, `tc-config.json` 의 `vplcUrl`, 개발은 `/vplc-api` 프록시). tc 토큰은 보내지 않는다.
- vplc 고침:
  - 메모리 쓰기에 `words`(워드 값 -32768~65535)를 더했다 — 예전 `data` 는 문자열 바이트라 숫자 값(예: D100=1234)을 쓸 수 없었다.
  - 읽은 바이트를 워드로 바꿀 때 낮은 바이트의 부호가 번졌다(`MemoryInfo.setWords` — 1234 가 -46 으로 보임). `Memory.writeWord(pos, low, high)` 도 같은 실수를 고쳤다. MC 프로토콜 경로는 다른 변환을 써서 tc 시험(test_plc_points)은 영향이 없었다.
  - 이름으로 하는 읽기·쓰기·지우기가 `null` 을 돌려줬다 → 이름을 아이디로 바꿔 같은 처리.
  - 게시되지 않은 가상 PLC 의 메모리를 건드리면 NPE 500 → 409.
- 문구: `ui-seed/i18n/35-tc-vplc.tsv`(4개 언어).
- 시험: e2e `test_vplc_http_methods.py` 35(메모리 4 더함 — vplc-e2e D950~ 를 쓰고 0 으로 되돌림), test_plc_points 23 그대로 통과. Playwright `e2e/vplc.spec.ts`(2) — 목록(등록·게시·시작·연결·정지·해제·삭제), 메모리(여러 값·10진 칸 고치기·비트·문자열, D900~ 를 쓰고 되돌림). 전체 화면 시험 18개 통과.
- 화면이 주소(`?id=`)를 다시 쓰면 같은 화면 탭이 하나 더 열려서, 목록에서 들어올 때만 읽고 다시 쓰지 않는다.
- 운영 반영: 운영 설치에는 vplc 가 없다(메뉴도 숨김). tc 는 문구 씨앗만 바뀌었다(jar 교체 때 함께). vplc 는 시험 장비라 재기동해도 된다.
