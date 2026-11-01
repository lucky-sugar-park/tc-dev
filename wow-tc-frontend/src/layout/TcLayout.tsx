import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Box, CssBaseline, ThemeProvider } from '@mui/material';
import {
  AppConfigProvider, DEFAULT_MAX_ALIVE, ScreenHost, type LayoutContext, type MenuNode, type TabHostController, type TabRef, type TabRetention,
} from 'wow-mes-frontend-framework';
import {
  AlertModal, Breadcrumbs, ChangePasswordPage, CommandBarSlotProvider, ConfirmModal, ConnectionSystemNotice, Footer,
  ForbiddenSnackbarHost, MAX_VISIBLE_TOASTS, MenuPanel, MenuRail, NotificationAlertStack, PwaUpdateSystemNotice, RichHeader,
  ScreenTabs, ShellActionsProvider, SystemBannerHost, ToastHost, UnsavedChangesGuardHost, applyPermissionFilter,
  basePathOf, getTheme, showAlert, useBackendMenu, useI18nStore, usePermissions, useSessionStore, useSettingsStore,
  useTabController, useTabsStore, useThemeStore, useUiRoutes,
} from 'wow-mes-frontend-shared';
import { TcLoginPage } from './TcLoginPage';
import { TcSettingsDialog } from './TcSettingsDialog';

/**
 * tc 셸 (docs/frontend-design.md 결정 F1). mes-dev shared 의 {@code DefaultLayout} 과 같은 짜임(머리말·메뉴 레일·패널·빵 부스러기·탭·
 * 바닥줄·설정·알림·SSE)이고 다른 것만 바꿨다.
 * <ul>
 *   <li>제목 "WOW-TC", 로그인은 tc 로그인 화면(회원가입·비밀번호 찾기가 없다).</li>
 *   <li>메뉴·화면 경로는 코드에 고정(결정 U2) — 서버의 /api/menus·/api/ui-routes 는 빈 목록을 돌려줘 셸이 코드 것을 쓴다.</li>
 *   <li>탭 보존 정책(MES 운영 옵션)·장식 없는 화면은 쓰지 않는다 — shared 가 그 도우미를 내보내지 않고 tc 에 필요 없다.</li>
 *   <li>설정 창은 tc 것({@link TcSettingsDialog}) — 시스템 탭·챗봇 도우미가 없고 화면 배치 미리보기가 WOW-TC 화면이다.</li>
 * </ul>
 */
export const TcLayout = ({ config }: LayoutContext): ReactNode => <Gated config={config} />;

function Gated({ config }: LayoutContext): ReactElement {
  // 로그인 전에도 다국어를 읽는다(/api/i18n/messages 는 공개)
  const lang = useI18nStore((s) => s.lang);
  const loadMessages = useI18nStore((s) => s.loadMessages);
  useEffect(() => { void loadMessages(lang); }, [lang, loadMessages]);

  const token = useSessionStore((s) => s.token);
  const user = useSessionStore((s) => s.user);
  const mode = useThemeStore((s) => s.mode);
  if (!token) {
    return (
      <ThemeProvider theme={getTheme(mode)}>
        <CssBaseline />
        <TcLoginPage />
      </ThemeProvider>
    );
  }
  // 비밀번호를 바꿔야 하는 계정은 셸을 그리지 않는다(백엔드도 다른 API 를 막는다)
  if (user?.passwordChangeRequired === true) {
    return (
      <ThemeProvider theme={getTheme(mode)}>
        <CssBaseline />
        <ChangePasswordPage />
      </ThemeProvider>
    );
  }
  return <Shell config={config} />;
}

const EMPTY_MENU: MenuNode[] = [];

/**
 * 내용이 같으면 앞의 참조를 그대로 — 토큰을 다시 받을 때마다 shared 가 /api/menus·/api/ui-routes 를 다시 받아 새 배열을 주는데,
 * 프레임워크 AppRouter 가 uiRoutes 가 바뀌면 화면 컴포넌트(lazy)를 새로 만들어 화면 전체가 다시 그려졌다("로딩…" 깜빡임, 열린 대화상자·입력이 사라짐).
 */
function useStableValue<T>(value: T): T {
  const ref = useRef(value);
  if (ref.current !== value && !sameJson(ref.current, value)) ref.current = value;
  return ref.current;
}

function sameJson(a: unknown, b: unknown): boolean {
  try {
    return JSON.stringify(a) === JSON.stringify(b);
  } catch {
    return false;  // 순환 참조 등 — 다르다고 본다
  }
}

function groupOfPath(menu: MenuNode[], pathname: string): string | undefined {
  const hit = (node: MenuNode): boolean => node.path === pathname || (node.children ?? []).some(hit);
  return menu.find((g) => hit(g))?.key;
}

function Shell({ config }: LayoutContext): ReactElement {
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const mode = useThemeStore((s) => s.mode);
  const tabsEnabled = useSettingsStore((s) => s.tabsEnabled);
  const tabPosition = useSettingsStore((s) => s.tabPosition);
  const tabOrder = useSettingsStore((s) => s.tabOrder);
  const openTabs = useTabsStore((s) => s.openTabs);

  // 권한은 서버 값만(/api/me/permissions — tc 역할 표 + 화면 권한 uiroute.tc.*, 결정 F3)
  const { status: permStatus, perms, load: loadPermissions } = usePermissions();
  const permissionsStatus = permStatus === 'idle' ? 'loading' : permStatus;

  const backendMenu = useStableValue(useBackendMenu());
  const fullMenu = backendMenu && backendMenu.length ? backendMenu : config.menu;
  const menu = useMemo(
    () => (permissionsStatus === 'ready' ? applyPermissionFilter(fullMenu, perms) : EMPTY_MENU),
    [permissionsStatus, fullMenu, perms],
  );
  const uiRoutes = useStableValue(useUiRoutes());
  const tabHostRef = useRef<TabHostController>(null);
  // 탭을 옮겨 다녀도 화면 상태를 지킨다(화면이 tabRetention 을 정하면 그것) — MES 의 운영 옵션 정책은 쓰지 않는다
  const retentionOf = useCallback(
    (tab: TabRef): TabRetention => config.screens.find((s) => s.key === tab.key)?.tabRetention ?? 'keep',
    [config.screens],
  );
  const hostTabs = useMemo(() => openTabs.map((t) => ({ key: t.key, path: t.path, id: t.id })), [openTabs]);

  // 모르는 주소 → 알리고 홈으로
  const alertedRef = useRef<string | null>(null);
  useEffect(() => {
    if (!uiRoutes) return;
    const base = basePathOf(pathname + search);
    const known = config.screens.some((s) => s.path === base) || uiRoutes.some((r) => r.path === base);
    if (known) { alertedRef.current = null; return; }
    if (alertedRef.current === base) return;
    alertedRef.current = base;
    showAlert({
      severity: 'warning',
      messageCode: 'i18n.route.undefined',
      message: '정의되지 않은 화면입니다. 홈 화면으로 이동합니다.',
      onClose: () => navigate('/', { replace: true }),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uiRoutes, pathname, search]);

  const [panelOpen, setPanelOpen] = useState(true);
  const [cmdSlot, setCmdSlot] = useState<HTMLElement | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [selectedGroupKey, setSelectedGroupKey] = useState<string>(() => config.menu[0]?.key ?? '');

  useEffect(() => {
    const g = groupOfPath(menu, pathname);
    if (g) setSelectedGroupKey(g);
  }, [pathname, menu]);

  useEffect(() => {
    const groups = menu.filter((g) => !g.divider);
    if (groups.length && !groups.some((g) => g.key === selectedGroupKey)) setSelectedGroupKey(groups[0].key);
  }, [menu, selectedGroupKey]);

  useTabController(fullMenu, config.screens, uiRoutes);
  const selectedGroup = menu.find((g) => g.key === selectedGroupKey);
  const shellActions = useMemo(() => ({ openSettings: () => setSettingsOpen(true) }), []);

  const appConfigValue = useMemo(
    () => ({
      ...config,
      permissions: perms,
      permissionsStatus,
      onRetryPermissions: () => { void loadPermissions(); },
      uiRoutes: uiRoutes ?? undefined,
    }),
    [config, perms, permissionsStatus, loadPermissions, uiRoutes],
  );

  return (
    <AppConfigProvider value={appConfigValue}>
      <ShellActionsProvider value={shellActions}>
        <CommandBarSlotProvider value={{ slot: cmdSlot }}>
          <ThemeProvider theme={getTheme(mode)}>
            <CssBaseline />
            <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
              <RichHeader title="WOW-TC" onToggleMenu={() => setPanelOpen((o) => !o)} />
              <SystemBannerHost />
              <ConnectionSystemNotice />
              <PwaUpdateSystemNotice />
              <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
                <MenuRail
                  nodes={menu}
                  selectedGroupKey={selectedGroupKey}
                  onSelectGroup={(key) => { setSelectedGroupKey(key); setPanelOpen(true); }}
                  onOpenSettings={() => setSettingsOpen(true)}
                />
                {panelOpen && <MenuPanel group={selectedGroup} nodes={menu} />}
                <Box component="main" sx={{ flex: 1, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
                  {tabsEnabled && tabPosition === 'top' && tabOrder === 'tabFirst' && <ScreenTabs position="top" />}
                  <Breadcrumbs menu={fullMenu} screens={config.screens} uiRoutes={uiRoutes ?? undefined} endSlotRef={setCmdSlot} />
                  {tabsEnabled && tabPosition === 'top' && tabOrder === 'cmdFirst' && <ScreenTabs position="top" />}
                  <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', p: 0.5 }}>
                    <ScreenHost
                      screens={config.screens}
                      permissions={appConfigValue.permissions}
                      mode="policy"
                      retentionOf={retentionOf}
                      maxAlive={DEFAULT_MAX_ALIVE}
                      controllerRef={tabHostRef}
                      openTabs={hostTabs}
                      uiRoutes={uiRoutes ?? undefined}
                      permissionsStatus={appConfigValue.permissionsStatus}
                      onRetryPermissions={appConfigValue.onRetryPermissions}
                      forbiddenRequestPath={null}
                    />
                  </Box>
                  {tabsEnabled && tabPosition === 'bottom' && <ScreenTabs position="bottom" />}
                </Box>
              </Box>
              <Footer />
            </Box>
            <TcSettingsDialog open={settingsOpen} onClose={() => setSettingsOpen(false)} />
            <AlertModal />
            <ConfirmModal />
            <ToastHost maxVisible={MAX_VISIBLE_TOASTS} />
            <UnsavedChangesGuardHost tabHost={tabHostRef} />
            <NotificationAlertStack />
            <ForbiddenSnackbarHost hideRequestButton />
            {config.globalOverlay}
          </ThemeProvider>
        </CommandBarSlotProvider>
      </ShellActionsProvider>
    </AppConfigProvider>
  );
}
