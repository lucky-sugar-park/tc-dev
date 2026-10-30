package com.wow.be.tc.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.wow.be.framework.security.ClientAddressResolver;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.apiclient.ApiClientService;
import com.wow.be.tc.apiclient.IpRanges;
import com.wow.be.tc.apiclient.VerifiedApiClient;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code X-API-Key} 헤더로 타 시스템을 인증한다 (설계 §6.2, §6.3). PLC 요청 경로({@link TcSecurityConfig#machineApiChain})에만 걸린다.
 *
 * <ul>
 *   <li>헤더가 없으면 아무것도 하지 않는다 — 다음 필터가 Bearer 토큰을 본다.</li>
 *   <li>헤더가 있는데 틀리면 401. 어느 경우든 문구가 같다. IP 별 실패 수를 세어 대입 시도를 막는다(429).</li>
 *   <li>키는 맞는데 허용되지 않은 IP 면 403.</li>
 * </ul>
 *
 * <p>빈으로 등록하지 않는다 — 빈이면 Spring Boot 가 서블릿 필터로도 자동 등록해 모든 요청에 걸린다.
 */
@Slf4j
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";

	private final ApiClientService apiClientService;

	private final ApiRequestLimiter limiter;

	private final ClientAddressResolver addressResolver;

	ApiKeyAuthenticationFilter(ApiClientService apiClientService, ApiRequestLimiter limiter, ClientAddressResolver addressResolver) {
		this.apiClientService = apiClientService;
		this.limiter = limiter;
		this.addressResolver = addressResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(HEADER);
		if (apiKey == null || apiKey.isBlank()) {
			chain.doFilter(request, response);
			return;
		}

		String ip = addressResolver.resolve(request);
		if (limiter.isBlocked(ip)) {
			response.setHeader("Retry-After", "60");
			SecurityErrors.write(request, response, 429, ToolControlException.RATE_LIMITED,
				"인증 실패가 많아 잠시 요청을 받지 않습니다. 잠시 뒤 다시 시도하세요.");
			return;
		}

		Optional<VerifiedApiClient> verified = apiClientService.verify(apiKey, ip);
		if (verified.isEmpty()) {
			limiter.recordFailure(ip);
			SecurityErrors.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
				ToolControlException.API_KEY_INVALID, SecurityErrors.API_KEY_INVALID_MESSAGE);
			return;
		}
		VerifiedApiClient client = verified.get();
		if (!IpRanges.contains(client.allowedIps(), ip)) {
			log.info("API 클라이언트 IP 거부: client={}, ip={}", client.clientId(), ip);
			SecurityErrors.write(request, response, HttpServletResponse.SC_FORBIDDEN,
				ToolControlException.API_CLIENT_IP_FORBIDDEN, "이 주소에서는 호출할 수 없습니다: " + ip);
			return;
		}

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new ApiClientAuthentication(client));
		SecurityContextHolder.setContext(context);
		chain.doFilter(request, response);
	}
}
