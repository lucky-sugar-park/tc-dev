package com.wow.be.tc.security;

import java.io.IOException;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 보안 필터가 직접 쓰는 오류 응답. 컨트롤러 밖이라 GlobalExceptionHandler 를 거치지 않으므로 같은 본문 형식
 * {@code {errorCode, message, path, timestamp}} 을 여기서 만든다.
 */
final class SecurityErrors {

	/** API 키 실패 문구 — 어느 경우(형식·없는 키·폐기·만료·중지)인지 드러내지 않는다 (설계 §6.3) */
	static final String API_KEY_INVALID_MESSAGE = "API 키가 올바르지 않습니다.";

	private SecurityErrors() {
	}

	static void write(HttpServletRequest request, HttpServletResponse response, int status, String errorCode, String message)
			throws IOException {
		response.setStatus(status);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"errorCode\":" + quote(errorCode)
			+ ",\"message\":" + quote(message)
			+ ",\"path\":" + quote(request.getRequestURI())
			+ ",\"timestamp\":" + quote(Instant.now().toString()) + "}");
	}

	/**
	 * framework JwtFilter 와 같은 만료 응답. 화면(SPA)은 이 code 를 보고 refresh 토큰으로 재발급한다.
	 */
	static void writeExpiredToken(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"message\":\"Access token expired\",\"code\":\"EXPIRED_TOKEN\",\"status\":\"UNAUTHORIZED\"}");
	}

	private static String quote(String s) {
		if (s == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
		for (char c : s.toCharArray()) {
			switch (c) {
			case '"' -> sb.append("\\\"");
			case '\\' -> sb.append("\\\\");
			case '\n' -> sb.append("\\n");
			case '\r' -> sb.append("\\r");
			case '\t' -> sb.append("\\t");
			default -> {
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
			}
		}
		return sb.append('"').toString();
	}
}
