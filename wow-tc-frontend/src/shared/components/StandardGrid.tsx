import { Box } from '@mui/material';
import { DataGridPro, type DataGridProProps, type GridColDef, type GridValidRowModel } from '@mui/x-data-grid-pro';
import { TotalCountFooter } from './TotalCountFooter';

/** 쪽 넘김을 켤 때 고를 수 있는 쪽 크기 */
export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

/**
 * 표준 목록 표 — mes-dev MDM {@code StandardMasterGrid} 를 옮겼다(compact·세로 테두리·고정 칸·줄무늬·쓰지 않음 흐림·총 N건).
 * 표는 absolute inset:0 상자에 넣는다 — 스크롤바가 생겼다 없어지며 크기가 흔들려 무한 렌더(React #185)가 나는 것을 막는다(mes-dev 와 같은 이유).
 * {@code pagination} 이면 아래 줄이 총 N건 대신 쪽 넘김(쪽 크기·처음/끝 버튼)이 된다. 그 밖의 DataGrid 속성(인라인 편집 등)은 {@code gridProps} 로.
 */
export function StandardGrid<T extends GridValidRowModel>({ rows, columns, loading, getRowId, isInactive, onRowClick, selectedId, pinLeft, minHeight = 0, pagination, gridProps }: {
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
  gridProps?: Partial<DataGridProProps<T>>;
}) {
  return (
    <Box sx={{ flex: 1, position: 'relative', minHeight }}>
      <Box sx={{ position: 'absolute', inset: 0 }}>
        <DataGridPro<T>
          rows={rows} columns={columns} getRowId={getRowId} loading={loading}
          disableRowSelectionOnClick density="compact" showColumnVerticalBorder showCellVerticalBorder
          {...(pagination
            ? {
              pagination: true, pageSizeOptions: PAGE_SIZE_OPTIONS,
              // basePagination 의 나머지 속성은 MUI TablePagination 으로 넘어간다(타입에는 없다)
              slotProps: { basePagination: { showFirstButton: true, showLastButton: true } as never },
            }
            : { slots: { footer: TotalCountFooter }, slotProps: { footer: { total: rows.length } } })}
          initialState={{
            pinnedColumns: { left: pinLeft ?? [], right: ['actions'] },
            ...(pagination ? { pagination: { paginationModel: { page: 0, pageSize: PAGE_SIZE_OPTIONS[0] } } } : {}),
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
        />
      </Box>
    </Box>
  );
}
