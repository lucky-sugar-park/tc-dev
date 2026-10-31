package com.wow.be.tc.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 화면 → 권한 자원 (docs/frontend-design.md §5, 결정 F3). 프론트의 정적 메뉴·경로는 화면 키마다 권한 코드
 * {@code uiroute.<화면 키>} 를 본다(framework {@code resourceCodeOf}). 그 값을 이 표와 역할 표({@code TcPermissionChecker})로 만든다 —
 * 서버가 막는 것과 화면이 숨기는 것이 같은 표다.
 *
 * <p>화면 키는 프론트 {@code src/config.tsx} 와 같아야 한다. 자원이 null 이면 로그인한 누구나 본다(홈).
 */
public final class UiRoutePermissions {

	public static final String PREFIX = "uiroute.";

	/** 화면 키 → 자원 */
	static final Map<String, String> SCREENS = new LinkedHashMap<>();

	static {
		SCREENS.put("tc.home", null);
		SCREENS.put("tc.plc.connector", "tc.plc");
		SCREENS.put("tc.plc.interface", "tc.interface");
		SCREENS.put("tc.plc.interface-test", "action.tc.dispatch");
		SCREENS.put("tc.plc.monitor", "tc.plc");
		SCREENS.put("tc.delivery.rule", "tc.delivery");
		SCREENS.put("tc.delivery.webhook", "tc.webhook");
		SCREENS.put("tc.delivery.dbconn", "tc.dbconn");
		SCREENS.put("tc.ops.audit", "tc.audit");
		SCREENS.put("tc.ops.monitor", "tc.audit");
		SCREENS.put("tc.ops.schedule", "tc.schedule");
		SCREENS.put("tc.ops.confighist", "tc.confighist");
		SCREENS.put("tc.admin.user", "tc.user");
		SCREENS.put("tc.admin.apiclient", "tc.apiclient");
		SCREENS.put("tc.admin.option", "tc.option");
		SCREENS.put("tc.admin.i18n", "tc.option");
		SCREENS.put("tc.admin.code", "tc.option");
		SCREENS.put("tc.vplc.list", "tc.plc");
		SCREENS.put("tc.vplc.memory", "tc.plc");
	}

	private UiRoutePermissions() {
	}

	/**
	 * 자원 권한에 화면 권한을 더한다. 자원에 동작이 하나라도 있으면 그 화면을 볼 수 있다({@code A}) — 동작 글자를 그대로 붙여
	 * 화면의 버튼({@code ScreenAction})이 같은 기준으로 숨는다.
	 *
	 * @param resources 자원 → 동작(예 {@code tc.plc → "ACRUDX"}, {@code tc.audit → "R"})
	 */
	public static Map<String, String> withScreens(Map<String, String> resources) {
		if (resources.containsKey("*")) {
			return resources;  // 관리자 — 셸의 can() 이 * 로 모든 화면을 연다
		}
		Map<String, String> out = new LinkedHashMap<>(resources);
		SCREENS.forEach((screen, resource) -> {
			if (resource == null) {
				out.put(PREFIX + screen, "AR");
				return;
			}
			String actions = resources.get(resource);
			if (actions != null && !actions.isEmpty()) {
				out.put(PREFIX + screen, actions.indexOf('A') >= 0 ? actions : "A" + actions);
			}
		});
		return out;
	}
}
