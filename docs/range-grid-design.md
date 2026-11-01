# 큰 목록 표 — 구간(Range) 스크롤과 구간 이동

- 작성일: 2026-11-01 (v0.5.0)
- 대상: tc 화면 운영 › 송수신 기록 · 운영 › 스케줄 Job 실행 이력 · 운영 › 설정 변경 이력 · 가상 PLC › 가상 PLC 연결 이력
- **mes-dev 에도 적용할 내용이다** — §2(MUI 가 실제로 하는 일)와 §7(mes-dev 적용)을 먼저 읽는다.
- 관련: mes-dev `wow-mes-frontend-default-ui-sys/src/i18n/pages/I18nManagementPage.tsx`(다국어 관리), `wow-mes-frontend-default-ui-workflow/src/shared/useRangeGrid.ts`

## 1. 요구

1. 표 왼쪽 위에 제목·건수(글머리 포함).
2. 첫 칸에 번호.
3. 아래 쪽 넘김을 없애고, 세로 스크롤이 경계에 닿으면 앞·뒤 행을 서버에서 백그라운드로 받는다.
4. 표가 항상 일정한 양의 행만 쥐고 필요한 만큼 서버에서 받는다(메모리 고정).
5. 오른쪽 위에서 한 번에 받는 행 수를 고른다(50·100·200, 직접 입력).
6. 명령 바에 엑셀 다운로드.

## 2. MUI X DataGridPro(v9) 가 실제로 하는 일 — 코드로 확인

| 흔한 오해 | 실제 |
|---|---|
| Row virtualization 을 켜면 표가 보이는 몇백 행만 쥔다 | **그리는 DOM 만** 줄인다(보이는 행 + 앞뒤 조금). 표에 넘긴 행·받은 행은 모두 메모리(행 트리)에 남는다. 가상화는 원래 늘 켜져 있다 |
| 지연 로딩(`dataSource` + `lazyLoading`)은 보이는 구간만 메모리에 둔다 | 보이는 구간만 **요청**한다. 응답은 그 위치의 행만 바꾸고, **다른 위치에서 이미 받은 행은 지우지 않는다**(`useGridDataSourceLazyLoader.js` — "Previously fetched rows are kept in place"). 캐시를 꺼도(`dataSourceCache={null}`) 다시 요청할 뿐 행은 남는다 |
| 건수를 알려 주면(`rowCount`) 스크롤바만 길어진다 | VIEWPORT 모드에서는 받지 않은 자리마다 **스켈레톤 행 객체를 `rowCount` 만큼 만든다**(`addSkeletonRows`). 300만 건이면 객체 300만 개 |

그래서 "건수를 아는 지연 로딩"을 그대로 쓰면 큰 표에서 메모리가 건수에 비례한다. mes-dev 다국어 관리의 "메모리 유지 행 수"는 실제로는 **한 번에 받는 행 수**다(다국어는 수천~수만 건이라 문제가 드러나지 않았다).

### IndexedDB 에 두면?

해결이 안 된다. 행을 쥐는 것은 MUI 내부 트리이고, "그릴 때 IndexedDB 에서 꺼내는" 행 단위 연결점이 없다. `getRows` 뒤의 캐시로 두면 서버 요청만 줄고 메모리는 그대로다. 게다가 감사 데이터가 브라우저 디스크에 남고(공용 운영 PC), 계속 쌓이는 표라 저장본이 금세 낡는다.

## 3. 고른 방식 — C + 구간 이동

| 방식 | 내용 | 판단 |
|---|---|---|
| A 무한 스크롤 | 아래 끝에서 다음 구간을 붙인다 | 단순하지만 스크롤바가 전체를 나타내지 못하고 메모리가 내려간 만큼 는다 |
| B 페이지 창 직접 구현 | K 쪽만 쥐고 반대쪽을 버리며 스크롤 위치를 직접 보정 | 메모리 고정이지만 MUI 밖 구현이라 깨지기 쉽다 |
| C 건수를 아는 지연 로딩(mes-dev 다국어 방식) | 스크롤바가 전체, 보이는 구간만 요청 | 스켈레톤이 건수만큼 — 큰 표에 상한이 필요 |
| **C + 구간 이동(채택)** | 그리드는 **한 구간(10만 건)만** 다룬다(`rowCount = min(전체 − 구간 시작, 10만)`). 구간 안에서는 C. 구간을 넘는 데이터는 [이전·다음 구간] 버튼으로 같은 검색 결과 안에서 이동 | 메모리 상한 고정(스켈레톤·받은 행 모두 10만 이하), 끝까지 이동 가능, MUI 기능 그대로 |

상한(구간 크기)은 C 방식에서 피할 수 없다 — 스켈레톤 때문이다. 구간 이동은 상한에 닿았을 때 "조건을 좁히라"고 하는 대신 같은 결과 안에서 옮겨 가게 한다.

## 4. 서버 API 규약 (네 화면 공통)

```
POST …/range
{ "filter": { …화면 검색 조건… },
  "range":  { "start": 0, "limit": 100, "asOf": null, "withTotal": true } }
→ { "rows": [...], "total": 230000 | null, "asOf": 81234, "start": 0 }
```

| 항목 | 뜻 |
|---|---|
| `start`·`limit` | 검색 결과에서의 절대 위치(0부터)와 행 수. limit 1~1000. framework `RangeableRepository.findAllByRange`(offset·limit) 로 읽는다 |
| `asOf` | **스냅샷 기준**. 첫 요청은 비워 보내고, 서버가 정해 돌려준 값을 이후 요청에 그대로 보낸다. 그 뒤에 쌓인 행은 결과에 들지 않는다 — 최신순 목록에서 스크롤 중 새 기록이 들어와 행이 밀리거나 겹치고 번호가 틀어지는 것을 막는다. 증가 키가 있는 표는 그 키의 최댓값(송수신 감사 `ID`, 실행 이력 `SEQ_NO`, 연결 이력 `SEQ_NO`), 없는 표는 시각(설정 변경 이력 `CHANGED_AT`, 밀리초) |
| `withTotal` | 건수를 셀지. **첫 요청에서만 true** — 큰 표에서 `count(*)` 를 스크롤마다 다시 세지 않는다. 안 세면 `total` 은 null 이고 화면은 앞의 건수를 쓴다 |
| 정렬 | 화면마다 고정(최신순) + 키로 끝까지 결정적(같은 시각이면 키 내림차순) |
| 본문 | 송수신 기록은 목록에서 본문(`MSG_JSON`)을 빼고 보낸다(상세 창은 거래 ID 로 따로 조회). 엑셀에서 본문을 고르면 `includeBody` |

기존 쪽 단위 API(`page`·`size`)는 다른 곳(Job 화면의 실행 이력 탭, 상세 창)이 쓰므로 그대로 둔다.

## 5. 화면 (공유 부품 `RangeGrid` · `useRangeGrid`)

- **제목 줄**: 글머리 · 제목 · (전체 건수). 전체가 구간보다 크면 `[◀] 1~100,000 / 230,000 [▶]` 구간 이동. 오른쪽에 **한 번에 받는 행 수**(50·100·200, 직접 입력 10~1000).
- **번호 칸**: 검색 결과 안의 절대 위치(최신 = 1). 구간을 옮겨도 이어진다(100,001 …).
- **표**: `DataGridPro` + `dataSource` + `lazyLoading` + `dataSourceCache={null}`, 아래 쪽 넘김 없음. 응답의 `rowCount` 는 `min(전체 − 구간 시작, 구간 크기)`. mes-dev 와 같이 첫 응답 뒤 `dataSource` 를 한 번 바꿔 스켈레톤을 채운다(MUI v9 의 첫 응답 경쟁).
- **조건 바꿈·새로 고침**: 스냅샷·건수·구간을 버리고 처음부터(스크롤 맨 위).
- 받은 행은 구간 안에서만 쌓인다 — 구간을 옮기면 비운다. 그래서 메모리 최대치 = 구간 크기(10만) 행 + 스켈레톤.

## 6. 엑셀 다운로드

- 그리드의 구간과 상관없이 **서버에서 같은 조건·같은 스냅샷으로** 나눠 받아 만든다 — 화면에 보이던 결과와 파일이 같다.
- 범위: **지금 구간** 또는 **검색 결과 전체**. 송수신 기록은 본문 포함 여부를 고른다(기본 뺌).
- 진행률·취소. 받는 단위 1000행.
- 브라우저에서 .xlsx 로 만든다(shared `downloadExcel`) — **20만 건까지**. 넘으면 "기간을 좁히세요"라고 알린다. 엑셀 한 시트 최대 1,048,576행. 20만 건이 넘는 내려받기가 필요해지면 서버에서 CSV 를 흘려보내는 API 를 더한다(UTF-8 BOM).

## 7. mes-dev 적용

1. **다국어 관리·`useRangeGrid`**: "메모리 유지 행 수" → "한 번에 받는 행 수"로 이름·설명을 바로잡는다. 메모리는 고정되지 않는다(§2).
2. 건수가 수십만을 넘을 수 있는 목록(이력·로그류)은 §3 의 구간 이동을 넣는다 — `rowCount` 를 구간 크기로 자르고, 구간 시작(offset)을 `start` 에 더한다.
3. 서버 Range API 에 `asOf`(스냅샷)·`withTotal`(첫 요청만 건수)을 더한다. framework `RangeResponse` 의 `totalRowsCount` 는 원시형이라 "안 셈"을 나타내려면 별도 응답형이나 −1 약속이 필요하다.
4. 목록에 큰 본문 칼럼이 있으면 목록 응답에서 뺀다.
5. 구현은 tc `wow-tc-frontend/src/shared/components/RangeGrid.tsx`·`useRangeGrid.ts`, 백엔드 `com.wow.be.tc.utils.RangeQueries` 를 참고한다.

## 8. 구현 (tc, 2026-11-01)

| 곳 | 파일 |
|---|---|
| 서버 공통 | `wow-tc-backend/tc/.../utils/RangeQueries.java` — `byKey`(증가 키 스냅샷)·`byTime`(시각 스냅샷), 요청 `Request<F>{filter, range}`·결과 `Result<T>{rows, total, asOf, start}` |
| 송수신 기록 | `POST /api/v1/ifsvc/audit/search/query/range` `{filter, range, includeBody}` — `AuditQueryService.range`, 스냅샷 `ID` |
| 스케줄 Job 실행 이력 | `POST /api/v1/schedule-job/management/exec-histories/range` — `ScheduleJobExecHistoryRangeableRepository`, 스냅샷 `SEQ_NO` |
| 설정 변경 이력 | `POST /api/v1/config-history/range` — `ConfigChangeRangeableRepository`, 스냅샷 `CHANGED_AT`(ID 가 UUID) |
| 가상 PLC 연결 이력(vplc) | `POST /api/v1/vplc/management/connection-histories/range` — `ConnectionHistoryRange`(같은 모양), 스냅샷 `SEQ_NO` |
| 화면 공통 | `wow-tc-frontend/src/shared/components/RangeGrid.tsx` — `useRangeGrid`·`RangeGrid`·`RangeExportDialog` |
| 화면 | `ops/pages/AuditPage.tsx`(제목 "송수신 목록")·`ScheduleHistoryPage.tsx`·`ConfigHistoryPage.tsx`·`vplc/pages/VplcConnectionHistoryPage.tsx` |

- 설정 변경 이력은 목록이 앞뒤 JSON 으로 "바뀐 칸"을 계산하므로 본문을 그대로 보낸다(행이 작다).
- 송수신 기록의 결과 재전송은 고른 행을 그리드에서 읽는다(`apiRef.getSelectedRows()`).

## 9. 확인 (2026-11-01)

시험 DB `tc_boot` 에 송수신 기록 150,000건(본문 약 220바이트)을 넣고 시험 tc(:8097)·vplc(:8098)·dev 서버로 확인했다.

| 항목 | 결과 |
|---|---|
| 첫 요청 | 건수 150,000·asOf, 목록 본문 비어 있음, 0.15초 |
| 12만 번째 위치 | 0.19초(OFFSET 12만) |
| 스냅샷 | 새 행을 넣어도 같은 asOf 로는 건수·첫 행 그대로, asOf 없이 다시 읽으면 150,001·새 행이 맨 위 |
| limit 5000 요청 | 1000 으로 잘림 |
| 화면 e2e `range-grids.spec.ts` | 4개 통과 — 제목·건수·번호·쪽 넘김 없음, 스크롤로 6,001번째 근처를 받아 번호가 이어짐, 다음 구간 첫 행 100,001·이전 구간으로 1, 한 번에 받는 행 수 직접 입력(300), 엑셀 창(지금 구간·전체·본문 포함), 설정 변경 이력 .xlsx 내려받기 |
| 기존 화면 e2e | `ops.spec.ts` 송수신 기록·스케줄 Job 실행 이력·스케줄 Job 화면 실행 이력 탭 통과 |

**메모리**(Chrome, GC 뒤 JS 힙):

| 상태 | 힙 |
|---|---|
| 빈 결과 | 35.6 MB |
| 10만 구간을 연 직후(스켈레톤 10만) | 69.8 MB (+34) |
| 구간 안 세 곳(2만·5만·9만 번째)으로 스크롤한 뒤 | 114.7 MB |
| 조건을 바꿔 비운 뒤 | 48.6 MB |

구간 크기가 메모리 최대치를 정한다 — 구간이 없으면 결과 건수에 비례해 는다(§2).
