import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Box, TablePagination } from '@mui/material';
import { useI18n } from 'wow-mes-frontend-shared';
import { DataGridPro, type DataGridProProps, type GridColDef, type GridPaginationModel, type GridValidRowModel } from '@mui/x-data-grid-pro';
import { ListTitle } from './ListTitle';
import { TotalCountFooter } from './TotalCountFooter';
import { MoreActionsIcon, withActionTooltips } from './actionTooltips';

/** 쪽 넘김을 켤 때 고를 수 있는 쪽 크기 */
export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

/**
 * 제목 줄 오른쪽의 쪽 넘김 — 쪽 크기·n–m of N·처음/이전/다음/끝. 표의 아래 바닥줄 대신 쓴다.
 * 커넥터 화면처럼 표 밖(카드)과 쪽을 함께 쓰는 곳은 이것만 따로 놓는다.
 */
export function TopPagination({ count, model, onChange }: {
  count: number;
  model: GridPaginationModel;
  onChange: (m: GridPaginationModel) => void;
}) {
  return (
    <TablePagination component="div" count={count} page={model.page} rowsPerPage={model.pageSize}
      rowsPerPageOptions={PAGE_SIZE_OPTIONS} showFirstButton showLastButton
      onPageChange={(_e, p) => onChange({ ...model, page: p })}
      onRowsPerPageChange={(e) => onChange({ page: 0, pageSize: Number(e.target.value) })}
      // 제목 줄 높이에 맞춘다(기본 툴바 52px)
      sx={{ overflow: 'visible', '& .MuiTablePagination-toolbar': { minHeight: 32, pl: 0 }, '& .MuiTablePagination-actions': { ml: 1 } }} />
  );
}

/** 번호 칸 필드 */
export const ROW_NO_FIELD = '__rowNo';

/**
 * 번호 칸 — 지금 보이는 순서(검색·정렬 반영)의 위치. 쪽 넘김이면 앞 쪽들의 행 수를 더한다. 데이터가 아니라 화면 위치라 정렬·거르기는 없다.
 */
export function rowNumberColumn<T extends GridValidRowModel>(headerName: string): GridColDef<T> {
  return {
    field: ROW_NO_FIELD, headerName, width: 70, type: 'number', sortable: false, filterable: false, disableColumnMenu: true,
    renderCell: (p) => {
      const index = p.api.getRowIndexRelativeToVisibleRows(p.id);
      if (index == null || index < 0) return '';
      const model = p.api.state.pagination?.paginationModel;
      const offset = p.api.state.pagination?.enabled !== false && model ? model.page * model.pageSize : 0;
      return (offset + index + 1).toLocaleString();
    },
  };
}

/** 행 수가 줄어 지금 쪽이 비면 마지막 쪽으로 */
export function clampPage(m: GridPaginationModel, count: number): GridPaginationModel {
  const last = Math.max(0, Math.ceil(count / m.pageSize) - 1);
  return m.page > last ? { ...m, page: last } : m;
}

/**
 * 표준 목록 표 — mes-dev MDM {@code StandardMasterGrid} 를 옮겼다(compact·세로 테두리·고정 칸·줄무늬·쓰지 않음 흐림·총 N건).
 * 표는 absolute inset:0 상자에 넣는다 — 스크롤바가 생겼다 없어지며 크기가 흔들려 무한 렌더(React #185)가 나는 것을 막는다(mes-dev 와 같은 이유).
 * <ul>
 *   <li>{@code title} 이 있으면 표 위에 제목 줄(글머리·제목·건수)을 두고 그 오른쪽에 쪽 넘김을 둔다 — 아래 바닥줄은 없다.
 *       {@code titleExtra} 는 쪽 넘김 왼쪽에 놓인다. 쪽 상태는 gridProps 의 paginationModel 로 밖에서 쥘 수 있다.</li>
 *   <li>제목 없이 {@code pagination} 이면 아래 줄이 총 N건 대신 쪽 넘김(쪽 크기·처음/끝 버튼)이 된다.</li>
 * </ul>
 * 그 밖의 DataGrid 속성(인라인 편집 등)은 {@code gridProps} 로. 작업 칸의 아이콘(⋮ 포함)에는 이름을 툴팁으로 단다.
 */
export function StandardGrid<T extends GridValidRowModel>({ rows, columns, loading, getRowId, isInactive, onRowClick, selectedId, pinLeft, minHeight = 0, pagination, title, titleExtra, numbered, gridProps }: {
  rows: T[];
  columns: GridColDef<T>[];
  loading?: boolean;
  getRowId: (r: T) => string;
  isInactive?: (r: T) => boolean;
  onRowClick?: (row: T) => void;
  selectedId?: string;
  /** 왼쪽에 고정할 칸 */
  pinLeft?: string[];
  minHeight?: number;
  /** 쪽 넘김(클라이언트 쪽) — 쪽 상태는 gridProps 의 paginationModel 로 밖에서 쥘 수 있다 */
  pagination?: boolean;
  /** 표 위 제목 — 주면 제목 줄 오른쪽에 쪽 넘김 */
  title?: string;
  /** 제목 줄에서 쪽 넘김 왼쪽에 둘 것 */
  titleExtra?: ReactNode;
  /** 첫 칸에 번호(보이는 순서) — 왼쪽 고정 칸이 있으면 그 앞에 고정 */
  numbered?: boolean;
  gridProps?: Partial<DataGridProProps<T>>;
}) {
  const { t } = useI18n();
  const cols = useMemo(() => withActionTooltips(numbered ? [rowNumberColumn<T>(t('i18n.tc.schedule.no', '번호')), ...columns] : columns),
    [columns, numbered, t]);
  const pinned = numbered && pinLeft?.length ? [ROW_NO_FIELD, ...pinLeft] : (pinLeft ?? []);
  const [ownModel, setOwnModel] = useState<GridPaginationModel>({ page: 0, pageSize: PAGE_SIZE_OPTIONS[0] });
  const model = gridProps?.paginationModel ?? ownModel;
  const setModel = (m: GridPaginationModel) => {
    if (gridProps?.onPaginationModelChange) gridProps.onPaginationModelChange(m, {} as never);
    else setOwnModel(m);
  };
  // 걸러서 행이 줄면 빈 쪽에 남지 않게
  useEffect(() => {
    if (!title || gridProps?.paginationModel) return;
    setOwnModel((m) => clampPage(m, rows.length));
  }, [rows.length, title, gridProps?.paginationModel]);
  const paged = !!title || pagination;

  const grid = (
    <Box sx={{ flex: 1, position: 'relative', minHeight }}>
      <Box sx={{ position: 'absolute', inset: 0 }}>
        <DataGridPro<T>
          rows={rows} columns={cols} getRowId={getRowId} loading={loading}
          disableRowSelectionOnClick density="compact" showColumnVerticalBorder showCellVerticalBorder
          {...(paged
            ? {
              pagination: true, pageSizeOptions: PAGE_SIZE_OPTIONS,
              // basePagination 의 나머지 속성은 MUI TablePagination 으로 넘어간다(타입에는 없다)
              slots: { moreActionsIcon: MoreActionsIcon },
              slotProps: { basePagination: { showFirstButton: true, showLastButton: true } as never },
            }
            : { slots: { footer: TotalCountFooter, moreActionsIcon: MoreActionsIcon }, slotProps: { footer: { total: rows.length } } })}
          initialState={{
            pinnedColumns: { left: pinned, right: ['actions'] },
            ...(paged ? { pagination: { paginationModel: { page: 0, pageSize: PAGE_SIZE_OPTIONS[0] } } } : {}),
          }}
          onRowClick={onRowClick ? (p) => onRowClick(p.row as T) : undefined}
          getRowClassName={(p) => {
            const parity = p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd';
            const base = isInactive?.(p.row) ? `${parity} row--inactive` : parity;
            return selectedId != null && getRowId(p.row) === selectedId ? `${base} row--selected` : base;
          }}
          sx={{
            '& .row--inactive': { opacity: 0.55 },
            ...(onRowClick ? { '& .MuiDataGrid-row': { cursor: 'pointer' } } : {}),
          }}
          {...gridProps}
          {...(title ? { paginationModel: model, onPaginationModelChange: (m: GridPaginationModel) => setModel(m), hideFooter: true } : {})}
        />
      </Box>
    </Box>
  );

  if (!title) return grid;
  return (
    // 제목 줄과 표는 붙여 둔다(페이지의 다른 칸 사이 간격 8px 보다 좁게)
    <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
      <ListTitle title={title} count={rows.length}>
        {titleExtra}
        <TopPagination count={rows.length} model={model} onChange={setModel} />
      </ListTitle>
      {grid}
    </Box>
  );
}
