package com.wow.be.tc.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wow.be.tc.ToolControlException;

/**
 * 역할 검사. framework {@code @Requires} 를 tc 에 켜기 전(설계 §9 5단계)까지 관리자 전용 API 를 이걸로 막는다.
 */
public final class TcRoles {

	public static final String ADMIN = "ROLE_ADMIN";

	private TcRoles() {
	}

	/** 지금 사용자가 관리자가 아니면 403 */
	public static void requireAdmin() throws ToolControlException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean admin = auth != null && !(auth instanceof ApiClientAuthentication)
			&& auth.getAuthorities().stream().anyMatch(a -> ADMIN.equals(a.getAuthority()));
		if (!admin) {
			throw ToolControlException.of(ToolControlException.FORBIDDEN, "관리자만 할 수 있습니다.");
		}
	}
}
