package com.wow.be.tc.security;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.apiclient.VerifiedApiClient;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * API 클라이언트의 사용량 제한 (설계 §6.8). 초당 요청 한도와 동시 처리 한도를 넘으면 429 를 낸다.
 * 사용자 JWT 호출(화면의 시험 호출)에는 적용하지 않는다.
 *
 * <p>동시 처리 자리는 이 요청의 처리가 끝날 때 돌려준다. 비동기 요청({@code /request})은 접수 즉시 끝나므로
 * 사실상 동기 요청({@code /request-and-reply})을 막는 한도다.
 *
 * <p>빈으로 등록하지 않는다 — {@link ApiKeyAuthenticationFilter} 와 같은 이유.
 */
@Slf4j
class ApiClientQuotaFilter extends OncePerRequestFilter {

	private final ApiRequestLimiter limiter;

	ApiClientQuotaFilter(ApiRequestLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!(auth instanceof ApiClientAuthentication clientAuth)) {
			chain.doFilter(request, response);
			return;
		}
		VerifiedApiClient client = clientAuth.getClient();

		if (!limiter.tryRate(client)) {
			log.debug("초당 요청 한도 초과: client={}, limit={}", client.clientId(), client.rateLimitPerSec());
			response.setHeader("Retry-After", "1");
			SecurityErrors.write(request, response, 429, ToolControlException.RATE_LIMITED,
				"초당 요청 한도(" + client.rateLimitPerSec() + "건)를 넘었습니다.");
			return;
		}
		Semaphore slot = limiter.tryAcquireConcurrency(client);
		if (slot == null) {
			log.debug("동시 처리 한도 초과: client={}, limit={}", client.clientId(), client.maxConcurrent());
			response.setHeader("Retry-After", "1");
			SecurityErrors.write(request, response, 429, ToolControlException.CONCURRENCY_LIMITED,
				"동시 처리 한도(" + client.maxConcurrent() + "건)를 넘었습니다.");
			return;
		}
		try {
			chain.doFilter(request, response);
		} finally {
			slot.release();
		}
	}
}
