package com.wow.be.tc.security;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.wow.be.framework.security.authz.PermissionChecker;

/**
 * 역할 → 자원·동작 표 하나로 하는 권한 검사 (docs/auth-and-audit-design.md §4.2).
 *
 * <p>이 빈이 있으면 framework {@code AuthzWebConfig} 가 {@code @Requires} 인터셉터를 켠다. 사용자 수가 적어 DB·화면으로
 * 관리하는 권한 매트릭스는 두지 않는다. 역할은 지금 요청의 인증(JWT {@code auth} 클레임·API 키)에서 읽는다 — DB 를 읽지 않는다.
 *
 * <p>구현 계약: 모르는 사용자·자원은 예외 없이 거부한다(던지면 403 이 아니라 500 이 된다).
 */
@Component
public class TcPermissionChecker implements PermissionChecker {

	private static final String ALL = "ACRUDX";

	private static final String READ = "R";

	/** 운영자: 설정 자원은 전부, 감사·이력·DB 연결은 조회만, PLC 요청 가능 */
	private static final Map<String, String> OPERATOR = Map.of(
		"tc.plc", ALL,
		"tc.interface", ALL,
		"tc.webhook", ALL,
		"tc.delivery", ALL,
		"tc.schedule", ALL,
		"tc.audit", READ,
		"tc.confighist", READ,
		"tc.dbconn", READ,
		"action.tc.dispatch", "X");

	/** 조회 전용: 사용자·API 클라이언트·보안 설정을 뺀 조회 */
	private static final Map<String, String> VIEWER = Map.of(
		"tc.plc", READ,
		"tc.interface", READ,
		"tc.webhook", READ,
		"tc.delivery", READ,
		"tc.schedule", READ,
		"tc.audit", READ,
		"tc.confighist", READ);

	/** API 키 주체: PLC 요청만 */
	private static final Map<String, String> CLIENT = Map.of("action.tc.dispatch", "X");

	/** 자원 이름(403 문장용) */
	private static final Map<String, String> NAMES = Map.ofEntries(
		Map.entry("tc.plc", "PLC 커넥터"),
		Map.entry("tc.interface", "인터페이스"),
		Map.entry("tc.webhook", "WebHook"),
		Map.entry("tc.delivery", "결과 전달 정의"),
		Map.entry("tc.dbconn", "DB 연결"),
		Map.entry("tc.schedule", "스케줄 Job"),
		Map.entry("tc.audit", "송수신 감사"),
		Map.entry("tc.confighist", "설정 변경 이력"),
		Map.entry("tc.apiclient", "API 클라이언트"),
		Map.entry("tc.user", "사용자"),
		Map.entry("tc.option", "운영 옵션"),
		Map.entry("action.tc.dispatch", "PLC 요청"),
		// 관리자만(OPERATOR 표에 없다) — docs/redelivery-design.md R7
		Map.entry("action.tc.redeliver", "결과 재전송"));

	/** 기존 JWT 호출자 전환 스위치(설계 §6.10). 전환이 끝나 기본은 꺼짐 — 재기동 없이 다시 켤 수 있다 */
	private final DispatchTransitionOption transition;

	public TcPermissionChecker(DispatchTransitionOption transition) {
		this.transition = transition;
	}

	@Override
	public Decision check(String userId, String resourceCode, char action) {
		String name = NAMES.get(resourceCode);
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (userId == null || auth == null || !userId.equals(auth.getName())) {
			return Decision.deny(resourceCode, action, name, "인증 정보가 없거나 다른 사용자");
		}
		Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

		if (roles.contains("ROLE_ADMIN")) {
			return Decision.allow(resourceCode, action, name);
		}
		if (allows(OPERATOR, resourceCode, action) && (roles.contains("ROLE_OPERATOR") || roles.contains("ROLE_MANAGER"))) {
			return Decision.allow(resourceCode, action, name);
		}
		if (allows(VIEWER, resourceCode, action) && (roles.contains("ROLE_VIEWER") || roles.contains("ROLE_EMPLOYEE"))) {
			return Decision.allow(resourceCode, action, name);
		}
		// PLC 요청 권한(CLIENT 표)은 API 키 주체에만 준다 — 사용자 역할의 ROLE_CLIENT 는 전환이 끝나 더 쓰지 않는다(설계 §9 7단계)
		if (auth instanceof ApiClientAuthentication && allows(CLIENT, resourceCode, action)) {
			return Decision.allow(resourceCode, action, name);
		}
		if (!(auth instanceof ApiClientAuthentication) && allows(CLIENT, resourceCode, action) && transition.isOn()) {
			return Decision.allow(resourceCode, action, name);
		}
		return Decision.deny(resourceCode, action, name, "역할 " + roles + " 에 " + resourceCode + ":" + action + " 권한 없음");
	}

	private static boolean allows(Map<String, String> table, String resource, char action) {
		String actions = table.get(resource);
		return actions != null && actions.indexOf(action) >= 0;
	}
}
