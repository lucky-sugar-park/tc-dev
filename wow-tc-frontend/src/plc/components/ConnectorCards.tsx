import { useState, type ReactElement, type ReactNode } from 'react';
import { Box, Card, CardActions, CardContent, Chip, Divider, IconButton, ListItemIcon, Menu, MenuItem, Tooltip, Typography } from '@mui/material';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import SentimentSatisfiedAltIcon from '@mui/icons-material/SentimentSatisfiedAlt';
import SentimentNeutralIcon from '@mui/icons-material/SentimentNeutral';
import SentimentVeryDissatisfiedIcon from '@mui/icons-material/SentimentVeryDissatisfied';
import { useI18n } from 'wow-mes-frontend-shared';
import type { PlcConnectorInfo } from '../types/plc';

/** 표의 작업 칸과 카드 아래 버튼이 함께 쓰는 작업 하나 — {@code menu} 면 ⋯ 메뉴로 */
export type ConnectorAction = { key: string; icon: ReactElement; label: string; onClick: () => void; menu?: boolean; disabled?: boolean };

/** 연결 상태 얼굴 — 연결됨(초록)·멈춤/상태 없음(주황)·끊김(빨강)·해제(회색, 연결하지 않는 상태) */
function ConnectionFace({ row }: { row: PlcConnectorInfo }): ReactElement {
  const { t } = useI18n();
  const c = row.connection;
  const sx = { fontSize: 64 };
  if (!row.published) return <Tooltip title={t('i18n.tc.connector.released', '해제')}><SentimentNeutralIcon sx={{ ...sx, color: 'text.disabled' }} /></Tooltip>;
  if (!c || c.operStatus === 'PAUSED') {
    return <Tooltip title={c ? t('i18n.tc.connector.paused', '멈춤') : t('i18n.tc.connector.noState', '상태 없음')}><SentimentNeutralIcon color="warning" sx={sx} /></Tooltip>;
  }
  return c.activeChannels > 0
    ? <Tooltip title={`${t('i18n.tc.connector.connected', '연결')} ${c.activeChannels}`}><SentimentSatisfiedAltIcon color="success" sx={sx} /></Tooltip>
    : <Tooltip title={t('i18n.tc.connector.disconnected', '끊김')}><SentimentVeryDissatisfiedIcon color="error" sx={sx} /></Tooltip>;
}

function Field({ label, children }: { label: string; children: ReactNode }): ReactElement {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.4 }}>{label}</Typography>
      <Typography variant="body2" noWrap title={typeof children === 'string' ? children : undefined}>{children || '—'}</Typography>
    </Box>
  );
}

function ConnectorCard({ row, actions, selected, onSelect }: {
  row: PlcConnectorInfo;
  actions: ConnectorAction[];
  selected: boolean;
  onSelect: () => void;
}): ReactElement {
  const { t } = useI18n();
  const [menuAt, setMenuAt] = useState<HTMLElement | null>(null);
  const shown = actions.filter((a) => !a.menu);
  const menu = actions.filter((a) => a.menu);
  const frame = String(row.settings?.frame ?? (row.settings?.unitId != null ? `unit ${row.settings.unitId}` : ''));
  return (
    <Card variant="outlined" onClick={onSelect} aria-label={`connector-card-${row.name}`}
      sx={{
        display: 'flex', flexDirection: 'column', cursor: 'pointer', opacity: row.published ? 1 : 0.8,
        borderColor: selected ? 'primary.main' : undefined, boxShadow: selected ? (th) => `0 0 0 1px ${th.palette.primary.main}` : undefined,
      }}>
      <CardContent sx={{ pb: 1, flex: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, flex: 1, minWidth: 0 }} noWrap title={row.name}>{row.name}</Typography>
          <Chip size="small" variant="outlined" label={row.protocol} />
        </Box>
        <Divider sx={{ my: 1 }} />
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr auto', columnGap: 1, alignItems: 'center' }}>
          <Box sx={{ display: 'grid', rowGap: 0.75, minWidth: 0 }}>
            <Field label={t('i18n.tc.connector.host', 'PLC 주소')}>{`${row.hostIp}:${(row.ports ?? []).join(',')}`}</Field>
            <Field label={t('i18n.tc.connector.publishStatus', '발행 상태')}>
              <Chip size="small" color={row.published ? 'primary' : 'default'} label={row.published ? 'PUBLISHED' : 'RELEASED'} />
            </Field>
            <Field label={t('i18n.tc.connector.frame', '프레임·장치')}>{frame}</Field>
          </Box>
          <ConnectionFace row={row} />
        </Box>
        <Box sx={{ display: 'grid', rowGap: 0.75, mt: 0.75 }}>
          {row.connection?.lastError && (
            <Field label={t('i18n.tc.connector.lastError', '마지막 오류')}>
              <Box component="span" sx={{ color: 'error.main' }}>{row.connection.lastError}</Box>
            </Field>
          )}
          <Field label={t('i18n.tc.common.description', '설명')}>{row.description ?? ''}</Field>
        </Box>
      </CardContent>
      <CardActions sx={{ justifyContent: 'flex-end', pt: 0 }} onClick={(e) => e.stopPropagation()}>
        {shown.map((a) => (
          <Tooltip key={a.key} title={a.label}>
            <span><IconButton size="small" aria-label={a.label} disabled={a.disabled} onClick={a.onClick}>{a.icon}</IconButton></span>
          </Tooltip>
        ))}
        {menu.length > 0 && (
          <IconButton size="small" aria-label="more" onClick={(e) => setMenuAt(e.currentTarget)}><MoreHorizIcon fontSize="small" /></IconButton>
        )}
        <Menu anchorEl={menuAt} open={!!menuAt} onClose={() => setMenuAt(null)}>
          {menu.map((a) => (
            <MenuItem key={a.key} disabled={a.disabled} onClick={() => { setMenuAt(null); a.onClick(); }}>
              <ListItemIcon>{a.icon}</ListItemIcon>{a.label}
            </MenuItem>
          ))}
        </Menu>
      </CardActions>
    </Card>
  );
}

/** 커넥터 카드 목록 — 넓이에 따라 여러 줄로. 쪽 넘김은 부르는 쪽이 자른 rows 로 */
export function ConnectorCards({ rows, actionsFor, selected, onSelect }: {
  rows: PlcConnectorInfo[];
  actionsFor: (row: PlcConnectorInfo) => ConnectorAction[];
  selected?: string | null;
  onSelect: (name: string) => void;
}): ReactElement {
  const { t } = useI18n();
  if (rows.length === 0) {
    return <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>{t('i18n.tc.common.noData', '데이터가 없습니다')}</Typography>;
  }
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 2, p: 0.5 }}>
      {rows.map((r) => (
        <ConnectorCard key={r.name} row={r} actions={actionsFor(r)} selected={selected === r.name} onSelect={() => onSelect(r.name)} />
      ))}
    </Box>
  );
}
