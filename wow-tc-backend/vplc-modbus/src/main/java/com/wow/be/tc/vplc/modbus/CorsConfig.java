package com.wow.be.tc.vplc.modbus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS — tc 화면이 가상 장비 REST 를 바로 부른다(docs/frontend-design.md F8, vplc 와 같음). 허용 출처는 설정 {@code vplc.cors.allowed-origin-patterns}(쉼표).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] patterns;

	public CorsConfig(@Value("${vplc.cors.allowed-origin-patterns}") String patterns) {
		this.patterns = java.util.Arrays.stream(patterns.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toArray(String[]::new);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOriginPatterns(patterns).allowedMethods("*").allowedHeaders("*");
	}
}
