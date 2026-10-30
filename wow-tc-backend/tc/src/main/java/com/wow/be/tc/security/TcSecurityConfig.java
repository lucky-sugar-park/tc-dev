package com.wow.be.tc.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import com.wow.be.framework.security.JwtFilter;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.apiclient.ApiClientService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * tc 보안 체인 (설계 §6.7).
 *
 * <p>PLC 요청 두 경로만 따로 체인을 둔다. 이 체인은 API 키와 사용자 JWT 를 둘 다 받는다. 그 밖의 경로는 framework
 * {@code SecurityConfig} 의 체인(JWT)이 그대로 맡는다. 이 체인이 {@code @Order(1)} 이라 먼저 평가된다.
 *
 * <p>전환 기간(설계 §6.10)에는 JWT 쪽 역할을 제한하지 않는다(authenticated) — 지금 JWT 로 부르는 시스템 계정이 어떤 역할이든
 * 끊기지 않게 하기 위해서다. 모든 시스템이 API 키로 옮긴 뒤 역할을 좁힌다(설계 §9 7단계).
 */
@Configuration
public class TcSecurityConfig {

	static final String REQUEST_PATH = "/api/v1/ifsvc/request";

	static final String REQUEST_AND_REPLY_PATH = "/api/v1/ifsvc/request-and-reply";

	/**
	 * framework JwtFilter 의 서블릿 자동 등록을 끈다.
	 *
	 * <p>JwtFilter 는 framework 에서 {@code @Bean} 이라 Spring Boot 가 서블릿 필터로도 등록한다. framework 체인에서는
	 * 보안 체인 안의 실행이 먼저라 두 번째는 건너뛰지만, {@link #machineApiChain} 에는 JwtFilter 가 없으므로 서블릿 단계의
	 * JwtFilter 가 돌아 Bearer 헤더 없는 API 키 요청에 401(EXPIRED_TOKEN)을 낸다. framework 체인 안의 JwtFilter 는 그대로다.
	 */
	@Bean
	public FilterRegistrationBean<JwtFilter> jwtFilterServletRegistration(JwtFilter jwtFilter) {
		FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
		registration.setEnabled(false);
		return registration;
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
