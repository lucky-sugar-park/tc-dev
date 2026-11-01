import { useCallback, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactElement, type ReactNode } from 'react';
import { Box, IconButton, Tooltip } from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useI18n } from 'wow-mes-frontend-shared';

const read = (key: string): { width?: number; collapsed?: boolean } => {
  try { return JSON.parse(localStorage.getItem(key) ?? '{}') as { width?: number; collapsed?: boolean }; } catch { return {}; }
};
const write = (key: string, v: { width: number; collapsed: boolean }) => {
  try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* 저장 못 해도 화면은 그대로 */ }
};

/**
 * 왼쪽·오른쪽 두 칸 — 가운데 막대를 끌어 왼쪽 넓이를 바꾸고(키보드 ←·→ 도), 막대의 단추로 왼쪽을 접었다 편다.
 * 넓이·접힘은 이 브라우저에 기억한다(storageKey).
 */
export function SplitPane({ left, right, storageKey, initialWidth = 380, minWidth = 200, minRight = 320 }: {
  left: ReactNode;
  right: ReactNode;
  storageKey: string;
  initialWidth?: number;
  minWidth?: number;
  /** 오른쪽이 남겨야 할 최소 넓이 */
  minRight?: number;
}): ReactElement {
  const { t } = useI18n();
  const saved = read(storageKey);
  const [width, setWidth] = useState(saved.width ?? initialWidth);
  const [collapsed, setCollapsed] = useState(saved.collapsed ?? false);
  const rootRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{ startX: number; startWidth: number } | null>(null);

  const clamp = useCallback((w: number) => {
    const total = rootRef.current?.getBoundingClientRect().width ?? 0;
    const max = total > 0 ? Math.max(minWidth, total - minRight) : w;
    return Math.round(Math.min(max, Math.max(minWidth, w)));
  }, [minWidth, minRight]);

  const save = (w: number, c: boolean) => write(storageKey, { width: w, collapsed: c });
  const toggle = () => { const c = !collapsed; setCollapsed(c); save(width, c); };

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (collapsed || e.button !== 0) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    dragRef.current = { startX: e.clientX, startWidth: width };
  };
  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const d = dragRef.current;
    if (d) setWidth(clamp(d.startWidth + e.clientX - d.startX));
  };
  const onPointerUp = (e: PointerEvent<HTMLDivElement>) => {
    if (!dragRef.current) return;
    dragRef.current = null;
    e.currentTarget.releasePointerCapture(e.pointerId);
    save(width, collapsed);
  };
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (collapsed || (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight')) return;
    e.preventDefault();
    const w = clamp(width + (e.key === 'ArrowLeft' ? -20 : 20));
    setWidth(w);
    save(w, collapsed);
  };

  const label = collapsed ? t('i18n.tc.split.show', '왼쪽 펴기') : t('i18n.tc.split.hide', '왼쪽 접기');
  return (
    <Box ref={rootRef} sx={{ flex: 1, minHeight: 0, display: 'flex' }}>
      {!collapsed && <Box sx={{ width, flexShrink: 0, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>{left}</Box>}
      <Box
        role="separator" aria-orientation="vertical" aria-label={t('i18n.tc.split.resize', '왼쪽 넓이 조절')} tabIndex={collapsed ? -1 : 0}
        onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onKeyDown={onKeyDown}
        sx={{
          width: 14, flexShrink: 0, position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center',
          cursor: collapsed ? 'default' : 'col-resize', touchAction: 'none', outline: 'none',
          '& .split-bar': { position: 'absolute', top: 0, bottom: 0, left: 6, width: 2, borderRadius: 1, bgcolor: 'divider' },
          '&:hover .split-bar, &:focus-visible .split-bar': { bgcolor: collapsed ? 'divider' : 'primary.main' },
        }}>
        <Box className="split-bar" />
        <Tooltip title={label} placement="right">
          <IconButton size="small" aria-label={label} onClick={toggle} onPointerDown={(e) => e.stopPropagation()}
            sx={{ zIndex: 1, width: 20, height: 36, borderRadius: 1, border: 1, borderColor: 'divider', bgcolor: 'background.paper',
              '&:hover': { bgcolor: 'action.hover' } }}>
            {collapsed ? <ChevronRightIcon sx={{ fontSize: 16 }} /> : <ChevronLeftIcon sx={{ fontSize: 16 }} />}
          </IconButton>
        </Tooltip>
      </Box>
      <Box sx={{ flex: 1, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>{right}</Box>
    </Box>
  );
}
