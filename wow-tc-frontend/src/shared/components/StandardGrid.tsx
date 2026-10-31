import { Box } from '@mui/material';
import { DataGridPro, type GridColDef, type GridValidRowModel } from '@mui/x-data-grid-pro';
import { TotalCountFooter } from './TotalCountFooter';

/**
 * 표준 목록 표 — mes-dev MDM {@code StandardMasterGrid} 를 옮겼다(compact·세로 테두리·고정 칸·줄무늬·쓰지 않음 흐림·총 N건).
 * 표는 absolute inset:0 상자에 넣는다 — 스크롤바가 생겼다 없어지며 크기가 흔들려 무한 렌더(React #185)가 나는 것을 막는다(mes-dev 와 같은 이유).
 */
export function StandardGrid<T extends GridValidRowModel>({ rows, columns, loading, getRowId, isInactive, onRowClick, selectedId, pinLeft, minHeight = 0 }: {
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
}) {
  return (
    <Box sx={{ flex: 1, position: 'relative', minHeight }}>
      <Box sx={{ position: 'absolute', inset: 0 }}>
        <DataGridPro<T>
          rows={rows} columns={columns} getRowId={getRowId} loading={loading}
          disableRowSelectionOnClick density="compact" showColumnVerticalBorder showCellVerticalBorder
          slots={{ footer: TotalCountFooter }} slotProps={{ footer: { total: rows.length } }}
          initialState={{ pinnedColumns: { left: pinLeft ?? [], right: ['actions'] } }}
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
        />
      </Box>
    </Box>
  );
}
