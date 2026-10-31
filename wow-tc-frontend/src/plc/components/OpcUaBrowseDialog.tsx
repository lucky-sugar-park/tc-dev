import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert, Box, Breadcrumbs, Button, Checkbox, Chip, Dialog, DialogContent, LinearProgress, Link, List, ListItemButton, ListItemIcon,
  ListItemText, Typography,
} from '@mui/material';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { connectorApi } from '../services/plcApi';
import type { BrowseNode, PointInfo } from '../types/plc';

/** 점 이름 — 영문자·_ 로 시작하는 영숫자·_ . - (서버 규칙). 안 되는 글자는 _ 로 */
function pointName(n: BrowseNode): string {
  const raw = (n.browseName || n.displayName || 'p').replace(/[^A-Za-z0-9_.-]/g, '_');
  return /^[A-Za-z_]/.test(raw) ? raw : `p_${raw}`;
}

/**
 * OPC UA 노드 찾아보기 (docs/opcua-design.md O10) — 폴더(Object)를 눌러 들어가고, 변수를 골라 점으로 더한다.
 * 형은 서버 데이터 형으로 채우고(모르면 FLOAT64), 배열이면 개수를 정하게 둔다(1).
 */
export function OpcUaBrowseDialog({ open, connector, existing, onClose, onAdd }: {
  open: boolean;
  connector?: string;
  /** 이미 있는 점 주소 — 다시 고르지 않게 */
  existing: string[];
  onClose: () => void;
  onAdd: (points: PointInfo[]) => void;
}): ReactElement {
  const { t } = useI18n();
  const [path, setPath] = useState<{ nodeId: string | null; label: string }[]>([{ nodeId: null, label: 'Objects' }]);
  const [nodes, setNodes] = useState<BrowseNode[]>([]);
  const [picked, setPicked] = useState<Record<string, BrowseNode>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const here = path[path.length - 1];
  const load = useCallback(async (nodeId: string | null) => {
    if (!connector) return;
    setLoading(true);
    setError(null);
    try {
      setNodes(await connectorApi.browse(connector, nodeId));
    } catch (e) {
      setNodes([]);
      setError(extractApiError(e));
    } finally {
      setLoading(false);
    }
  }, [connector]);

  useEffect(() => {
    if (!open) return;
    setPath([{ nodeId: null, label: 'Objects' }]);
    setPicked({});
  }, [open, connector]);
  useEffect(() => { if (open) void load(here.nodeId); }, [open, here.nodeId, load]);

  const toggle = (n: BrowseNode) => setPicked((p) => {
    const next = { ...p };
    if (next[n.nodeId]) delete next[n.nodeId]; else next[n.nodeId] = n;
    return next;
  });

  const add = () => {
    onAdd(Object.values(picked).map((n) => ({
      name: pointName(n), address: n.nodeId, type: n.type ?? 'FLOAT64', count: 1, description: n.displayName ?? undefined,
    })));
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.opcua.browse', '노드 찾아보기')} — {connector}</DialogTitleClose>
      <DialogContent dividers sx={{ height: '60vh', display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Breadcrumbs>
          {path.map((p, i) => (i === path.length - 1
            ? <Typography key={i} variant="body2">{p.label}</Typography>
            : <Link key={i} component="button" variant="body2" onClick={() => setPath(path.slice(0, i + 1))}>{p.label}</Link>))}
        </Breadcrumbs>
        {loading && <LinearProgress />}
        {error && <Alert severity="error">{error}</Alert>}
        <Box sx={{ flex: 1, overflow: 'auto' }}>
          <List dense>
            {nodes.map((n) => {
              const used = existing.includes(n.nodeId);
              return n.hasChildren ? (
                <ListItemButton key={n.nodeId} onClick={() => setPath([...path, { nodeId: n.nodeId, label: n.displayName || n.browseName || n.nodeId }])}>
                  <ListItemIcon sx={{ minWidth: 36 }}><FolderOutlinedIcon fontSize="small" /></ListItemIcon>
                  <ListItemText primary={n.displayName || n.browseName} secondary={n.nodeId} />
                </ListItemButton>
              ) : (
                <ListItemButton key={n.nodeId} disabled={used} onClick={() => toggle(n)}>
                  <ListItemIcon sx={{ minWidth: 36 }}>
                    <Checkbox edge="start" size="small" checked={!!picked[n.nodeId] || used} tabIndex={-1} disableRipple
                      slotProps={{ input: { 'aria-label': `pick-${n.browseName}` } }} />
                  </ListItemIcon>
                  <ListItemText primary={n.displayName || n.browseName} secondary={n.nodeId} />
                  <Chip size="small" variant="outlined" label={`${n.type ?? n.dataType ?? '?'}${n.array ? '[]' : ''}`} />
                </ListItemButton>
              );
            })}
            {!loading && !error && nodes.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>{t('i18n.tc.opcua.noChildren', '아래 노드가 없습니다')}</Typography>
            )}
          </List>
        </Box>
      </DialogContent>
      <DialogFooter onClose={onClose}>
        <Button variant="contained" disabled={Object.keys(picked).length === 0} onClick={add}>
          {t('i18n.tc.opcua.addPoints', '점으로 더하기')} ({Object.keys(picked).length})
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
