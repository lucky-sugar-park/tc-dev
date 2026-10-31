package com.wow.be.tc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import com.wow.be.framework.security.ClientAddressResolver;
import com.wow.be.framework.security.JwTokenProvider;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.apiclient.ApiClientService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * tc 보안 체인 (설계 §6.7).
 *
 * <p>PLC 요청 두 경로만 따로 체인을 둔다. 이 체인은 API 키와 사용자 JWT 를 둘 다 받는다. 그 밖의 경로는 framework
 * {@code SecurityConfig} 의 체인(JWT)이 그대로 맡는다. 이 체인이 {@code @Order(1)} 이라 먼저 평가된다.
 *
 * <p>인증 없이 받는 경로(로그인·재발급·헬스 체크)는 framework 의 공개 경로 설정({@code app.security.public-paths})으로 연다.
 * JwtFilter 의 서블릿 자동 등록은 framework 0.8.46 부터 framework 가 끈다(docs/framework-core-0.8.46-changes.md §2·§3).
 *
 * <p>이 체인은 인증만 확인한다(authenticated). 누가 PLC 요청을 할 수 있는지는 {@code @Requires("action.tc.dispatch")} 와
 * {@link TcPermissionChecker} 가 정한다 — API 키 클라이언트와 ADMIN·OPERATOR 사용자. 전환 스위치
 * ({@link DispatchTransitionOption})를 켜면 로그인한 사용자 누구나 허용한다(설계 §6.10, §9 7단계).
 */
@Configuration
public class TcSecurityConfig {

	static final String REQUEST_PATH = "/api/v1/ifsvc/request";

	static final String REQUEST_AND_REPLY_PATH = "/api/v1/ifsvc/request-and-reply";

	/**
	 * 인스턴스 사이 PLC 교환({@code /internal/**}, docs/plc-ha-design.md §4.1) — 사용자 JWT·API 키를 받지 않는다. 요청 서명은
	 * 컨트롤러가 본문과 함께 확인한다(서명이 본문 해시를 덮으므로 본문을 읽는 곳에서).
	 */
	@Bean
	@Order(0)
	public SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher("/internal/**"))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> a.anyRequest().permitAll());
		return http.build();
	}

	@Bean
	@Order(1)
	public SecurityFilterChain machineApiChain(HttpSecurity http, ApiClientService apiClientService, ApiRequestLimiter limiter,
			JwTokenProvider tokenProvider, ClientAddressResolver addressResolver) throws Exception {
		PathPatternRequestMatcher.Builder mvc = PathPatternRequestMatcher.withDefaults();
		ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter(apiClientService, limiter, addressResolver);
		MachineBearerFilter bearerFilter = new MachineBearerFilter(tokenProvider, addressResolver);

		http
			.securityMatcher(new OrRequestMatcher(
				mvc.matcher(HttpMethod.POST, REQUEST_PATH),
				mvc.matcher(HttpMethod.POST, REQUEST_AND_REPLY_PATH)))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> a.anyRequest().authenticated())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, e) -> SecurityErrors.write(request, response,
					HttpServletResponse.SC_UNAUTHORIZED, "ERR_401", "인증이 필요합니다. X-API-Key 헤더나 Bearer 토큰을 보내세요."))
				.accessDeniedHandler((request, response, e) -> SecurityErrors.write(request, response,
					HttpServletResponse.SC_FORBIDDEN, ToolControlException.FORBIDDEN, "권한이 없습니다.")))
			.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(bearerFilter, ApiKeyAuthenticationFilter.class)
			.addFilterAfter(new ApiClientQuotaFilter(limiter), MachineBearerFilter.class);
		return http.build();
	}
}
