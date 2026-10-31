package com.wow.be.tc.vplc.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 전역 CORS 필터. 교차 출처 허용은 여기 한 곳에서만 정한다(컨트롤러의 {@code @CrossOrigin("*")} 는 뺐다).
 * 허용 출처는 설정 {@code vplc.cors.allowed-origin-patterns}(쉼표) — 예전에는 http://localhost:3000 만 코드에 있었다.
 */
@Configuration
@Slf4j
public class CorsFilterConfig {

	@Bean
	public CorsFilter corsFilter(@Value("${vplc.cors.allowed-origin-patterns}") String patterns) {
		log.debug("");

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		for (String p : patterns.split(",")) {
			if (!p.isBlank()) {
				config.addAllowedOriginPattern(p.trim());
			}
		}
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		config.setAllowCredentials(true);

		source.registerCorsConfiguration("/**", config);

		return new CorsFilter(source);
	}
}
