package com.wow.be.tc.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.wow.be.framework.security.ClientAddressResolver;
import com.wow.be.framework.security.JwTokenProvider;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * PLC 요청 경로에서 사용자 JWT 를 받는다. 화면의 시험 호출과, API 키로 옮기기 전의 기존 호출자(설계 §6.10)를 위해서다.
 *
 * <p>framework JwtFilter 와 같은 규칙으로 검증한다(서명·저장된 토큰·접속 주소). API 키로 이미 인증됐으면 건너뛴다.
 * 만료면 JwtFilter 와 같은 {@code EXPIRED_TOKEN} 응답을 내 화면이 재발급하게 한다. 토큰이 없거나 틀리면 인증 없이 넘겨
 * 보안 체인이 401 을 내게 한다.
 *
 * <p>빈으로 등록하지 않는다 — {@link ApiKeyAuthenticationFilter} 와 같은 이유.
 */
class MachineBearerFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwTokenProvider tokenProvider;

	private final ClientAddressResolver addressResolver;

	MachineBearerFilter(JwTokenProvider tokenProvider, ClientAddressResolver addressResolver) {
		this.tokenProvider = tokenProvider;
		this.addressResolver = addressResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication current = SecurityContextHolder.getContext().getAuthentication();
		String header = request.getHeader("Authorization");
		if ((current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken))
				|| header == null || !header.startsWith(BEARER)) {
			chain.doFilter(request, response);
			return;
		}

		String token = header.substring(BEARER.length());
		try {
			if (tokenProvider.validateToken(token, addressResolver.resolve(request), true)) {
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(tokenProvider.extractAuthentication(token));
				SecurityContextHolder.setContext(context);
			}
		} catch (ExpiredJwtException e) {
			SecurityErrors.writeExpiredToken(response);
			return;
		}
		chain.doFilter(request, response);
	}
}
