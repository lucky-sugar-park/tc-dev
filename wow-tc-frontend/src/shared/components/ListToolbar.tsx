import { useRef, type ReactNode } from 'react';
import { Divider, IconButton, InputAdornment, TextField } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { CommandBarActions, FilterBar, ScreenAction, useI18n } from 'wow-mes-frontend-shared';

/**
 * 목록 화면 위 — mes-dev MDM {@code MasterToolbar} 를 바탕으로 했다. 동작(새로 고침·검색·추가·엑셀)은 빵 부스러기 오른쪽 명령 줄로,
 * 검색은 필터 줄로. {@code filters} 로 검색칸 옆에 더 넣는다. 버튼은 화면 권한으로 숨는다({@code ScreenAction}).
 */
export function ListToolbar({ search, onSearch, onRefresh, onAdd, onExport, placeholder, filters, actions }: {
  search: string;
  onSearch: (v: string) => void;
  onRefresh: () => void;
  onAdd?: () => void;
  onExport?: () => void;
  placeholder?: string;
  /** 필터 줄에 더 넣을 칸 */
  filters?: ReactNode;
  /** 명령 줄에 더 넣을 버튼 */
  actions?: ReactNode;
}) {
  const { t } = useI18n();
  const searchRef = useRef<HTMLInputElement>(null);
  return (
    <>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={onRefresh} />
        <ScreenAction kind="search" onClick={() => searchRef.current?.focus()} />
        {(onAdd || onExport || actions) && <Divider orientation="vertical" flexItem sx={{ my: 0.75, mx: 0.25 }} />}
        {actions}
        {onAdd && <ScreenAction kind="add" onClick={onAdd} />}
        {onExport && <ScreenAction kind="excelDown" onClick={onExport} />}
      </CommandBarActions>
      <FilterBar onSearch={onRefresh} onReset={() => onSearch('')}>
        <TextField size="small" placeholder={placeholder ?? t('i18n.tc.common.searchPlaceholder', '이름 검색')} value={search}
          onChange={(e) => onSearch(e.target.value)} inputRef={searchRef}
          slotProps={{
            htmlInput: { 'aria-label': t('i18n.common.unifiedSearch', '통합검색') },
            input: {
              startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" color="action" /></InputAdornment>,
              endAdornment: search ? (
                <InputAdornment position="end">
                  <IconButton size="small" aria-label={t('i18n.common.clearSearch', '검색어 지우기')} onClick={() => onSearch('')}><ClearIcon fontSize="small" /></IconButton>
                </InputAdornment>
              ) : undefined,
            },
          }}
          sx={{ width: 280, '& .MuiInputBase-root': { fontSize: '0.8rem', height: 32 } }} />
        {filters}
      </FilterBar>
    </>
  );
}
