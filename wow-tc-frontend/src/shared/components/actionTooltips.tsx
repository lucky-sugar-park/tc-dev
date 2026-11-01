import { cloneElement, forwardRef, isValidElement, type ReactElement, type ReactNode } from 'react';
import { Box, Tooltip, type SvgIconProps } from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import type { GridActionsColDef, GridColDef, GridRowParams, GridValidRowModel } from '@mui/x-data-grid-pro';
import { useI18n } from 'wow-mes-frontend-shared';

/**
 * 아이콘에 툴팁 — 작업 칸의 아이콘 버튼은 aria-label 만 있고 툴팁이 없다(MUI GridActionsCellItem). 아이콘을 감싼 span 의 ::after 가
 * 버튼 전체를 덮어 버튼 어디에 올려도 뜬다. GridActionsCellItem 이 아이콘에 주는 fontSize 를 그대로 넘긴다.
 */
const TipIcon = forwardRef<HTMLSpanElement, { title: ReactNode; icon: ReactElement<SvgIconProps>; fontSize?: SvgIconProps['fontSize'] }>(
  function TipIcon({ title, icon, fontSize, ...rest }, ref) {
    return (
      <Tooltip title={title}>
        <Box component="span" ref={ref} {...rest}
          sx={{ display: 'inline-flex', '&::after': { content: '""', position: 'absolute', inset: 0 } }}>
          {cloneElement(icon, fontSize ? { fontSize } : {})}
        </Box>
      </Tooltip>
    );
  },
);

/** 작업 칸의 ⋮(더보기) 아이콘 — 표의 {@code slots.moreActionsIcon} */
export function MoreActionsIcon({ fontSize }: { fontSize?: SvgIconProps['fontSize'] }): ReactElement {
  const { t } = useI18n();
  return <TipIcon title={t('i18n.tc.common.more', '더보기')} icon={<MoreVertIcon />} fontSize={fontSize} />;
}

/**
 * 작업 칸(type actions)의 바로 보이는 아이콘에 그 이름(label)을 툴팁으로 단다. 메뉴 안 항목은 이름이 이미 보여 그대로 둔다.
 * {@code StandardGrid} 가 모든 표에 쓰고, DataGridPro 를 바로 쓰는 표는 이것과 {@link MoreActionsIcon} 을 함께 쓴다.
 */
export function withActionTooltips<T extends GridValidRowModel>(columns: GridColDef<T>[]): GridColDef<T>[] {
  return columns.map((c) => {
    if (c.type !== 'actions') return c;
    const col = c as GridActionsColDef<T>;
    return {
      ...col,
      getActions: (params: GridRowParams<T>) => col.getActions(params).map((a) => {
        const p = a.props as { showInMenu?: boolean; label?: ReactNode; icon?: ReactElement<SvgIconProps> };
        if (p.showInMenu || !isValidElement(p.icon)) return a;
        return cloneElement(a, { icon: <TipIcon title={p.label} icon={p.icon} /> } as never);
      }),
    } as GridColDef<T>;
  });
}
