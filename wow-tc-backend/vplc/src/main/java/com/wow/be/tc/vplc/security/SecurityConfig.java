package com.wow.be.tc.vplc.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

/**
 * vplc 보안 설정 — 모든 요청을 인증 없이 허용한다(레거시와 같다).
 *
 * <p>vplc 는 tc 개발·통합 테스트용 시뮬레이터라 레거시도 permitAll 이었다. framework-core 의 JWT 보안 설정은
 * 컴포넌트 스캔에서 빠져 있어(VirtualPlcApplication 참고) 이 체인 하나만 동작한다.
 * 운영망에 띄울 일이 생기면 framework JWT 보안을 붙여야 한다(알려진 미비점).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Autowired
	private CorsFilter corsFilter;

	// 일단 모든 request를 통과시키도록 설정함 ( 나중에 인증/권한 체크 해야 함 )
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.httpBasic( configurer->configurer.disable() )
			.addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
			.csrf( csrfConfig -> csrfConfig.disable() )
			.cors( corsConfig -> corsConfig.disable() )
			.authorizeHttpRequests ( authorize -> {
				authorize
					.anyRequest().permitAll();
			})
			.headers (
				headersConfigurer -> {
					headersConfigurer.frameOptions (
						HeadersConfigurer.FrameOptionsConfig::sameOrigin
					);
				}
			)
			.sessionManagement( sessionManagementConfigurer -> {
				sessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
			});
		return http.build();
	}
}
