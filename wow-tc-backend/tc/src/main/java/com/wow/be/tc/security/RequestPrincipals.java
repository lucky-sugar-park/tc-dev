package com.wow.be.tc.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.apiclient.VerifiedApiClient;
import com.wow.be.tc.dispatch.TcMessageRequest;

/**
 * PLC 요청의 호출자 판단 (설계 §6.4, §6.5).
 *
 * <ul>
 *   <li>요청에 실제 호출자를 {@code client:{id}} / {@code user:{id}} 로 찍는다. 본문의 sender 는 호출자가 마음대로 쓰는 값이라
 *       감사에는 이 값을 따로 남긴다.</li>
 *   <li>API 클라이언트면 opcode 와 pushUrl 이 허용 범위 안인지 본다.</li>
 * </ul>
 *
 * <p>컨트롤러(요청 스레드)에서 부른다. 비동기 디스패치는 다른 스레드에서 돌아 보안 컨텍스트가 없으므로, 디스패처 인터셉터가 아니라
 * 여기서 판단하고 결과를 요청에 실어 보낸다.
 */
public final class RequestPrincipals {

	public static final String SYSTEM = "system";

	private RequestPrincipals() {
	}

	/** 지금 스레드의 인증으로 호출자 문자열을 만든다. 인증이 없으면 {@code system}(스케줄 Job 등 내부). */
	public static String current() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof ApiClientAuthentication client) {
			return "client:" + client.getName();
		}
		if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
				&& auth.getName() != null && !auth.getName().isBlank()) {
			return "user:" + auth.getName();
		}
		return SYSTEM;
	}

	/** 호출자를 요청에 찍고, API 클라이언트면 허용 범위를 확인한다. */
	public static void stampAndAuthorize(TcMessageRequest request) throws ToolControlException {
		request.setPrincipal(current());
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof ApiClientAuthentication auth)) {
			return;
		}
		VerifiedApiClient client = auth.getClient();
		String opcode = request.getOpcode();
		if (opcode != null && !opcode.isBlank() && !client.allowsOpcode(opcode)) {
			throw ToolControlException.of(ToolControlException.OPCODE_FORBIDDEN,
				"이 클라이언트에 허용되지 않은 opcode 입니다: " + opcode);
		}
		if (request.wantsCallback() && !client.allowsPushUrl(request.getPushUrl())) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"이 클라이언트에 허용되지 않은 pushUrl 입니다: " + request.getPushUrl());
		}
	}
}
