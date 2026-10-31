# wow-tc-frontend

tc(PLC 게이트웨이) 화면. mes-dev 프론트의 `wow-mes-frontend-framework`·`wow-mes-frontend-shared`(Nexus `0.8.46`)를 쓰는 독립 앱이다.
설계: [../docs/frontend-design.md](../docs/frontend-design.md).

## 실행

```bash
npm install            # .npmrc 의 Nexus group 저장소, legacy-peer-deps
npm run dev            # http://localhost:5180 — /api → tc(8087), /vplc-api → vplc(8088)
npm run build          # 타입 검사 + dist/
```

- 개발 서버가 넘길 곳은 `TC_DEV_API`·`VPLC_DEV_API` 로 바꾼다.
- MUI X Pro 라이선스는 `VITE_MUI_X_LICENSE_KEY`(`.env.local`, 저장소에 넣지 않는다). 없으면 표에 개발 워터마크가 뜬다.

## 화면 시험(Playwright)

실제 tc·vplc 가 떠 있고 백엔드 e2e 준비(`setup_e2e.py` — 커넥터 plc-e2e·RAW 인터페이스)가 된 상태에서, 설치된 Chrome 으로 돈다.

```bash
TC_UI_URL=http://localhost:5180 TC_UI_E2E_ID=<관리자 아이디> TC_UI_E2E_PASSWORD_FILE=<비밀번호 파일> npx playwright test
```

비밀번호는 파일로만 받는다(출력하지 않는다). 시험은 읽기·미리보기 위주이고, 만드는 인터페이스(`e2e.ui.*`)는 끝에 지운다.

## 배포

`dist/` 를 tc 의 `TC_UI_DIR` 폴더에 그대로 복사한다(예: `robocopy dist <폴더> /MIR`). **tc 재기동은 필요 없다.**
배포마다 바꾸는 값은 그 폴더의 `tc-config.json`:

| 키 | 내용 |
|---|---|
| `vplcUrl` | 가상 PLC(vplc) 주소. 비우면 가상 PLC 메뉴를 숨긴다(운영 설치). vplc 가 다른 출처면 vplc 에 CORS 를 연다 |

## 구조

- `src/screens.ts` — 화면 키·경로·메뉴(정적). 화면 키는 백엔드 `UiRoutePermissions` 와 같아야 한다(권한 코드 `uiroute.<키>`).
- `src/config.tsx` — framework `AppConfig`(화면·메뉴·셸). 만든 화면은 `PAGES` 에, 나머지는 "준비 중".
- `src/layout/` — `TcLayout`(mes shared `DefaultLayout` 바탕, 제목·로그인만 다름), `TcLoginPage`.
- 기능 폴더(`home/` …)마다 `pages/`·`components/`·`services/`·`types/`.
- 화면 문구는 `t('i18n.tc.…', '한국어')` — 4개 언어 값은 백엔드 `ui-seed/i18n/30-tc-screens.tsv`.
