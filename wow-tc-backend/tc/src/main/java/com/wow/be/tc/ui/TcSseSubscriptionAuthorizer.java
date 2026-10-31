package com.wow.be.tc.ui;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.wow.be.framework.sse.SseSubscriptionAuthorizer;
import com.wow.be.tc.apiclient.ApiKeyExpiryNotifier.ApiKeyExpiringEvent;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditEvent;
import com.wow.be.tc.plc.management.PlcConnectionPushEvent;
import com.wow.be.tc.security.TcPermissionChecker;

/**
 * SSE 구독 권한 (docs/frontend-design.md F2). 연결({@code /api/sse/connect})은 누구나 되지만 이벤트는 구독해야 받는다 — 구독은
 * 로그인한 사용자가 그 자원을 읽을 수 있을 때만. 표에 없는 이벤트(셸의 {@code notification} 등)는 받게 둔다.
 */
@Component
public class TcSseSubscriptionAuthorizer implements SseSubscriptionAuthorizer {

	/** 이벤트 → 읽어야 하는 자원 */
	private static final Map<String, String> RESOURCES = Map.of(
		ExchangedMessageAuditEvent.EVENT_KIND, "tc.audit",
		PlcConnectionPushEvent.EVENT_KIND, "tc.plc",
		ApiKeyExpiringEvent.EVENT_KIND, "tc.apiclient");

	@Override
	public boolean canSubscribe(String eventName, String userId) {
		String resource = RESOURCES.get(eventName);
		if (resource == null) {
			return true;
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (userId == null || auth == null || !userId.equals(auth.getName())) {
			return false;
		}
		Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
		Map<String, String> perms = TcPermissionChecker.effective(roles);
		String actions = perms.containsKey("*") ? perms.get("*") : perms.get(resource);
		return actions != null && actions.indexOf('R') >= 0;
	}
}
