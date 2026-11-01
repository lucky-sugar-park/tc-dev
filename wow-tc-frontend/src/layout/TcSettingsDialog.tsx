import { useEffect, useState, type ReactElement, type ReactNode } from 'react';
import {
  Box, Card, CardActionArea, Dialog, DialogContent, FormControlLabel, List, ListItemButton, ListItemText, Radio, RadioGroup, Slider,
  Switch, Typography,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import type { TabRetention } from 'wow-mes-frontend-framework';
import {
  DialogFooter, DialogTitleClose, DraggablePaper, THEME_DEFS, operationOptionsClient, useI18n, useLov, useSettingsStore, useThemeStore,
  type TabOrder, type TabPosition, type TabUserPref,
} from 'wow-mes-frontend-shared';
import { menuI18nCode } from '../screens';

/**
 * tc 설정 창 — shared {@code SettingsDialog} 를 옮겼다(그 부품은 섹션을 따로 내보내지 않는다). 다른 점:
 * <ul>
 *   <li>시스템 탭이 없다 — tc 의 운영 옵션은 관리 › 운영 옵션 화면에서 바꾼다.</li>
 *   <li>챗봇 도우미가 없다 — tc 셸에는 도우미 버튼이 없다.</li>
 *   <li>화면 배치 미리보기는 WOW-TC 화면(PLC › 인터페이스)이다.</li>
 * </ul>
 * 값은 shared 의 설정·테마 저장소에 그대로 저장한다.
 */
type Cat = 'theme' | 'notify' | 'tab';

/** 팝업 본문 고정 높이(px) — 카테고리를 바꿔도 크기가 변하지 않게, 넘치면 오른쪽만 스크롤 */
const BODY_HEIGHT = 460;

export function TcSettingsDialog({ open, onClose }: { open: boolean; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [cat, setCat] = useState<Cat>('theme');
  const cats: { id: Cat; label: string }[] = [
    { id: 'theme', label: t('i18n.settings.cat.theme', '테마/모양') },
    { id: 'notify', label: t('i18n.settings.cat.notify', '알림') },
    { id: 'tab', label: t('i18n.settings.cat.tab', '화면 배치') },
  ];
  return (
    <Dialog
      open={open} onClose={onClose} fullWidth maxWidth="md"
      PaperComponent={DraggablePaper}
      slotProps={{ paper: { sx: { height: 'auto', maxHeight: 'calc(100% - 64px)' } } }}
    >
      <DialogTitleClose onClose={onClose}>{t('i18n.settings.title', '설정')}</DialogTitleClose>
      <DialogContent dividers sx={{ p: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', height: BODY_HEIGHT }}>
          <List sx={{ width: 180, borderRight: 1, borderColor: 'divider', flexShrink: 0, overflowY: 'auto' }} disablePadding>
            {cats.map((c) => (
              <ListItemButton key={c.id} selected={c.id === cat} onClick={() => setCat(c.id)}>
                <ListItemText primary={c.label} />
              </ListItemButton>
            ))}
          </List>
          <Box sx={{ flex: 1, p: 3, overflowY: 'auto' }}>
            {cat === 'theme' && <ThemeSection />}
            {cat === 'notify' && <NotifySection />}
            {cat === 'tab' && <TabSection />}
          </Box>
        </Box>
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography>
      {children}
    </Box>
  );
}

function ThemeSection(): ReactElement {
  const { t } = useI18n();
  const mode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);
  return (
    <Section title={t('i18n.settings.cat.theme', '테마/모양')}>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 1.5 }}>
        {THEME_DEFS.map((d) => (
          <Card key={d.key} variant="outlined" sx={{ borderColor: d.key === mode ? 'primary.main' : 'divider', borderWidth: d.key === mode ? 2 : 1 }}>
            <CardActionArea onClick={() => setMode(d.key)} sx={{ p: 1 }}>
              <Box sx={{ height: 36, borderRadius: 1, mb: 1, display: 'flex', overflow: 'hidden', border: 1, borderColor: 'divider' }}>
                <Box sx={{ width: '60%', bgcolor: d.bgDefault }} />
                <Box sx={{ width: '20%', bgcolor: d.primary }} />
                <Box sx={{ width: '20%', bgcolor: d.secondary }} />
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Typography variant="caption" noWrap>
                  {d.key === 'light' ? t('i18n.settings.theme.lightDefault', '라이트 (기본)')
                    : d.key === 'dark' ? t('i18n.settings.theme.darkDefault', '다크 (기본)')
                    : d.label}
                </Typography>
                {d.key === mode && <CheckCircleIcon color="primary" sx={{ fontSize: 16 }} />}
              </Box>
              <Typography variant="caption" color="text.secondary">{d.mode === 'light' ? t('i18n.settings.theme.light', '라이트') : t('i18n.settings.theme.dark', '다크')}</Typography>
            </CardActionArea>
          </Card>
        ))}
      </Box>
    </Section>
  );
}

function NotifySection(): ReactElement {
  const { t } = useI18n();
  const popup = useSettingsStore((s) => s.notifyPopupEnabled);
  const sec = useSettingsStore((s) => s.notifyAutoCloseSec);
  const setPopup = useSettingsStore((s) => s.setNotifyPopupEnabled);
  const setSec = useSettingsStore((s) => s.setNotifyAutoCloseSec);
  return (
    <Section title={t('i18n.settings.cat.notify', '알림')}>
      <Box>
        <FormControlLabel
          control={<Switch checked={popup} onChange={(e) => setPopup(e.target.checked)} />}
          label={t('i18n.settings.notify.popup', '알림 팝업 표시')}
        />
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', pl: 0.5 }}>
          {t('i18n.settings.notify.popupDesc', '끄면 알림 팝업이 화면에 표시되지 않습니다.')}
        </Typography>
      </Box>
      <Box sx={{ mt: 3, opacity: popup ? 1 : 0.5, pointerEvents: popup ? 'auto' : 'none' }}>
        <Typography variant="subtitle2" gutterBottom>{t('i18n.settings.notify.autoClose', '자동 닫힘')}</Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
          {sec === 0
            ? t('i18n.settings.notify.autoCloseManual', '직접 닫을 때까지 유지됩니다.')
            : `${sec}${t('i18n.settings.notify.autoCloseValue', '초 후 자동으로 닫힙니다.')}`}
        </Typography>
        <Box sx={{ px: 1, maxWidth: 420 }}>
          <Slider
            value={sec} onChange={(_, v) => setSec(v as number)} min={0} max={60} step={1}
            marks={[
              { value: 0, label: t('i18n.settings.notify.manual', '수동') },
              { value: 8, label: '8s' },
              { value: 30, label: '30s' },
              { value: 60, label: '60s' },
            ]}
            valueLabelDisplay="auto"
            valueLabelFormat={(v) => (v === 0 ? t('i18n.settings.notify.manual', '수동') : `${v}s`)}
          />
        </Box>
      </Box>
    </Section>
  );
}

/** 레이아웃 프리셋 — 탭 유무·위치·순서(shared 와 같은 4개) */
type LayoutPreset = 'none' | 'A' | 'B' | 'C';
type RegionKey = 'header' | 'tabs' | 'cmd' | 'filter' | 'grid';

const PRESET_REGIONS: Record<LayoutPreset, RegionKey[]> = {
  none: ['header', 'cmd', 'filter', 'grid'],
  A: ['header', 'tabs', 'cmd', 'filter', 'grid'],
  B: ['header', 'cmd', 'tabs', 'filter', 'grid'],
  C: ['header', 'cmd', 'filter', 'grid', 'tabs'],
};

function toPreset(tabsEnabled: boolean, tabPosition: TabPosition, tabOrder: TabOrder): LayoutPreset {
  if (!tabsEnabled) return 'none';
  if (tabPosition === 'bottom') return 'C';
  return tabOrder === 'cmdFirst' ? 'B' : 'A';
}

/** 미리보기·썸네일 한 띠 — WOW-TC 의 PLC › 인터페이스 화면. compact = 카드 썸네일 */
function RegionBand({ region, label, compact }: { region: RegionKey; label?: string; compact?: boolean }): ReactElement {
  const { t } = useI18n();
  const common = { display: 'flex', alignItems: 'center', flexShrink: 0, px: compact ? 0.5 : 1, gap: 0.5 } as const;
  const h = compact ? 8 : 26;
  const iface = t(menuI18nCode('tc.plc.interface'), '인터페이스');
  if (region === 'header')
    return <Box sx={{ ...common, height: h, bgcolor: 'primary.main', color: 'primary.contrastText', fontSize: compact ? 0 : 11, fontWeight: 700 }}>{!compact && 'WOW-TC'}</Box>;
  if (region === 'tabs')
    return (
      <Box sx={{ ...common, height: compact ? 9 : 22, bgcolor: 'action.hover', borderTop: 1, borderBottom: 1, borderColor: 'divider', color: 'text.secondary', fontSize: compact ? 0 : 10 }}>
        {compact ? '▭ ▭' : <span>{t(menuI18nCode('tc.plc.connector'), '커넥터')} · <b>{iface}</b> · {t(menuI18nCode('tc.delivery.webhook'), 'WebHook')}</span>}
      </Box>
    );
  if (region === 'cmd')
    return (
      <Box sx={{ ...common, height: compact ? 10 : 30, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', boxShadow: (th) => `inset 3px 0 0 ${th.palette.primary.main}`, color: 'primary.main', fontSize: compact ? 0 : 11, fontWeight: 600 }}>
        {!compact && <span>{t(menuI18nCode('tc.home'), '홈')} › {t(menuI18nCode('tc.plc'), 'PLC')} › <b>{iface}</b></span>}
        {!compact && <Box sx={{ ml: 'auto', display: 'flex', gap: 0.5 }}>{[0, 1, 2, 3].map((i) => <Box key={i} sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: 'primary.main', opacity: 0.5 }} />)}</Box>}
      </Box>
    );
  if (region === 'filter')
    return (
      <Box sx={{ ...common, height: compact ? 9 : 28, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', boxShadow: (th) => `inset 3px 0 0 ${th.palette.success.main}`, color: 'success.main', fontSize: compact ? 0 : 10 }}>
        {!compact && <><Box sx={{ width: 44, height: 10, borderRadius: 5, bgcolor: 'action.selected' }} /><Box sx={{ width: 44, height: 10, borderRadius: 5, bgcolor: 'action.selected' }} /><Box sx={{ ml: 'auto', fontSize: 10 }}>{label ?? t('i18n.filter.search', '검색')}</Box></>}
      </Box>
    );
  return (
    <Box sx={{ flex: 1, minHeight: compact ? 12 : 40, bgcolor: 'action.hover', p: compact ? 0.5 : 1, display: 'flex', flexDirection: 'column', gap: compact ? 0.25 : 0.5, opacity: 0.6 }}>
      {(compact ? [0, 1] : [0, 1, 2, 3]).map((i) => <Box key={i} sx={{ height: compact ? 2 : 7, borderRadius: 3, bgcolor: 'text.disabled', width: i === 3 ? '70%' : '100%' }} />)}
    </Box>
  );
}

function LayoutPreview({ preset, searchLabel, height }: { preset: LayoutPreset; searchLabel: string; height: number }): ReactElement {
  return (
    <Box aria-label="layout-preview" sx={{ height, border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
      {PRESET_REGIONS[preset].map((r, i) => <RegionBand key={`${r}-${i}`} region={r} label={r === 'filter' ? searchLabel : undefined} />)}
    </Box>
  );
}

function TabSection(): ReactElement {
  const { t } = useI18n();
  const tabsEnabled = useSettingsStore((s) => s.tabsEnabled);
  const tabPosition = useSettingsStore((s) => s.tabPosition);
  const tabOrder = useSettingsStore((s) => s.tabOrder);
  const allowDup = useSettingsStore((s) => s.allowDuplicateTabs);
  const setTabsEnabled = useSettingsStore((s) => s.setTabsEnabled);
  const setTabPosition = useSettingsStore((s) => s.setTabPosition);
  const setTabOrder = useSettingsStore((s) => s.setTabOrder);
  const setAllowDup = useSettingsStore((s) => s.setAllowDuplicateTabs);

  const searchLabel = t('i18n.filter.search', '검색');
  const current = toPreset(tabsEnabled, tabPosition, tabOrder);

  const PRESETS: { key: LayoutPreset; name: string; badge?: string; desc: string }[] = [
    { key: 'none', name: t('i18n.settings.preset.none', '탭 없음'), desc: t('i18n.settings.preset.none.desc', '화면 탭 미사용. Command Bar만 상단에.') },
    { key: 'A', name: t('i18n.settings.preset.a', '탭 상단 · 탭이 위'), desc: t('i18n.settings.preset.a.desc', '탭으로 화면을 고르면 그 아래 해당 화면 Command Bar.') },
    { key: 'B', name: t('i18n.settings.preset.b', '탭 상단 · Command Bar가 위'), badge: t('i18n.settings.preset.default', '기본'), desc: t('i18n.settings.preset.b.desc', '제목·경로가 헤더 바로 아래 고정, 탭은 그 아래.') },
    { key: 'C', name: t('i18n.settings.preset.c', '탭 하단'), desc: t('i18n.settings.preset.c.desc', '탭을 그리드 아래 맨 하단에 배치.') },
  ];

  const applyPreset = (key: LayoutPreset) => {
    switch (key) {
      case 'none': setTabsEnabled(false); break;
      case 'A': setTabsEnabled(true); setTabPosition('top'); setTabOrder('tabFirst'); break;
      case 'B': setTabsEnabled(true); setTabPosition('top'); setTabOrder('cmdFirst'); break;
      case 'C': setTabsEnabled(true); setTabPosition('bottom'); break;
    }
  };

  return (
    <Section title={t('i18n.settings.cat.tab', '화면 배치')}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {PRESETS.map((p) => {
            const sel = p.key === current;
            return (
              <Card key={p.key} variant="outlined" sx={{ borderColor: sel ? 'primary.main' : 'divider', borderWidth: sel ? 2 : 1, boxShadow: sel ? (th) => `0 0 0 3px ${th.palette.primary.main}22` : 'none' }}>
                <CardActionArea onClick={() => applyPreset(p.key)} aria-current={sel} sx={{ p: 1, display: 'flex', gap: 1.25, alignItems: 'center', justifyContent: 'flex-start' }}>
                  <Box sx={{ width: 72, flexShrink: 0 }}>
                    <Box sx={{ height: 48, border: 1, borderColor: 'divider', borderRadius: 0.5, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                      {PRESET_REGIONS[p.key].map((r, i) => <RegionBand key={`${r}-${i}`} region={r} compact />)}
                    </Box>
                  </Box>
                  <Box sx={{ minWidth: 0 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{p.name}</Typography>
                      {p.badge && <Box component="span" sx={{ fontSize: 10, bgcolor: 'primary.main', color: 'primary.contrastText', borderRadius: 2, px: 0.75, py: 0.1, fontWeight: 700 }}>{p.badge}</Box>}
                      {sel && <CheckCircleIcon color="primary" sx={{ fontSize: 15, ml: 'auto' }} />}
                    </Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.35, mt: 0.25 }}>{p.desc}</Typography>
                  </Box>
                </CardActionArea>
              </Card>
            );
          })}
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75, fontWeight: 700 }}>{t('i18n.settings.preset.preview', '미리보기')}</Typography>
          <LayoutPreview preset={current} searchLabel={searchLabel} height={220} />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, lineHeight: 1.5 }}>
            {t('i18n.settings.preset.note', 'Command Bar(경로·제목·액션)와 Filter Bar는 항상 상단 고정입니다. 프리셋은 탭의 유무·위치·순서만 바꿉니다.')}
          </Typography>
        </Box>
      </Box>
      <Box sx={{ mt: 3, pt: 2, borderTop: 1, borderColor: 'divider' }}>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>{t('i18n.settings.tab.advanced', '고급')}</Typography>
        <TabUserPrefControl disabled={!tabsEnabled} />
        <Box>
          <FormControlLabel disabled={!tabsEnabled} control={<Switch checked={allowDup} onChange={(e) => setAllowDup(e.target.checked)} />} label={t('i18n.settings.allowDuplicateTabs', '동일 화면 다중 탭 허용')} />
        </Box>
      </Box>
    </Section>
  );
}

// shared 의 탭 정책(layout/tabPolicy)과 같은 값 — shared 가 내보내지 않는다
const TAB_USER_PREFS: readonly TabUserPref[] = ['follow', 'light', 'retain'];
const TAB_USER_PREF_LOV_GROUP = 'common.code.ui.tab_user_pref';
const TAB_RETENTION_LOV_GROUP = 'common.code.ui.tab_retention';
const DEFAULT_RETENTION_OPTION_ID = 'oper.ui.tab.defaultRetention';
const DEFAULT_RETENTION: TabRetention = 'refresh';
const USER_PREF_FALLBACK: Record<TabUserPref, string> = { follow: '시스템 기본 따름', light: '가볍게', retain: '되도록 유지' }; // i18n-ignore 공통코드 라벨의 한국어 폴백
const RETENTION_FALLBACK: Record<TabRetention, string> = { keep: '보존', refresh: '다시 읽기', reset: '새로' }; // i18n-ignore 공통코드 라벨의 한국어 폴백

const toRetention = (v: unknown): TabRetention | undefined => (v === 'keep' || v === 'refresh' || v === 'reset' ? v : undefined);

/** 관리자 기본 탭 유지 정책(운영 옵션 oper.ui.tab.defaultRetention) — "시스템 기본 따름 (현재: …)" 표시용. 못 읽으면 다시 읽기 */
function useAdminDefaultRetention(): TabRetention {
  const [value, setValue] = useState<TabRetention>(DEFAULT_RETENTION);
  useEffect(() => {
    let alive = true;
    let seq = 0;
    const load = () => {
      const mine = ++seq;
      operationOptionsClient.getEffective([DEFAULT_RETENTION_OPTION_ID])
        .then((r) => toRetention(r[DEFAULT_RETENTION_OPTION_ID]) ?? DEFAULT_RETENTION)
        .catch(() => DEFAULT_RETENTION)
        .then((v) => { if (alive && mine === seq) setValue(v); });
    };
    load();
    const unsubscribe = operationOptionsClient.subscribe((ids) => {
      if (ids === null || ids.includes(DEFAULT_RETENTION_OPTION_ID)) load();
    });
    return () => { alive = false; unsubscribe(); };
  }, []);
  return value;
}

/** 탭 유지 — 다른 탭으로 옮겼다 돌아올 때 화면을 어떻게 둘지 */
function TabUserPrefControl({ disabled }: { disabled: boolean }): ReactElement {
  const { t } = useI18n();
  const pref = useSettingsStore((s) => s.tabUserPref);
  const setPref = useSettingsStore((s) => s.setTabUserPref);
  const { options: prefOptions } = useLov(TAB_USER_PREF_LOV_GROUP);
  const { options: retentionOptions } = useLov(TAB_RETENTION_LOV_GROUP);
  const adminDefault = useAdminDefaultRetention();
  const prefLabel = (p: TabUserPref) =>
    prefOptions.find((o) => o.value === `${TAB_USER_PREF_LOV_GROUP}.${p}`)?.label
      ?? t(`i18n.${TAB_USER_PREF_LOV_GROUP}.${p}`, USER_PREF_FALLBACK[p]);
  const defaultLabel = retentionOptions.find((o) => o.value === `${TAB_RETENTION_LOV_GROUP}.${adminDefault}`)?.label
    ?? t(`i18n.${TAB_RETENTION_LOV_GROUP}.${adminDefault}`, RETENTION_FALLBACK[adminDefault]);
  const currentWord = t('i18n.settings.tabUserPref.current', '현재');
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography id="settings-tab-user-pref" variant="body2" sx={{ fontWeight: 600 }}>
        {t('i18n.settings.tabUserPref', '탭 유지')}
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.4 }}>
        {t('i18n.settings.tabUserPref.desc', '다른 탭으로 옮겼다 돌아올 때 화면을 어떻게 둘지 고릅니다. 관리자가 화면별로 정한 예외가 있으면 그것이 먼저입니다.')}
      </Typography>
      <RadioGroup aria-labelledby="settings-tab-user-pref" value={pref} onChange={(_, v) => setPref(v as TabUserPref)}>
        {TAB_USER_PREFS.map((p) => (
          <FormControlLabel
            key={p} value={p} disabled={disabled}
            control={<Radio size="small" />}
            label={p === 'follow' ? `${prefLabel(p)} (${currentWord}: ${defaultLabel})` : prefLabel(p)}
          />
        ))}
      </RadioGroup>
    </Box>
  );
}
