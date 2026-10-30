package com.wow.be.tc.vplc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 전역 CORS 필터. 교차 출처 허용은 여기 한 곳에서만 정한다(컨트롤러의 {@code @CrossOrigin("*")} 는 뺐다).
 * 허용 출처는 레거시 그대로 http://localhost:3000.
 */
@Configuration
@Slf4j
public class CorsFilterConfig {

	@Bean
	public CorsFilter corsFilter() {
		log.debug("");

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOriginPattern("http://localhost:3000");
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		config.setAllowCredentials(true);

		source.registerCorsConfiguration("/**", config);

		return new CorsFilter(source);
	}
}
