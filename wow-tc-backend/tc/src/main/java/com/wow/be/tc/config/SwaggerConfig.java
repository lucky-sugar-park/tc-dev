package com.wow.be.tc.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import lombok.RequiredArgsConstructor;

@OpenAPIDefinition
(
		info=@Info(
				title="Tool Controller V1 API Specification",
				description="Tool Controller V1 API Specification",
				version="v1"
		)
)
@RequiredArgsConstructor
@Configuration
public class SwaggerConfig {
	
	@Bean
	public GroupedOpenApi chatOpenApi() {
		// "/api/v1/**", "/api/auth/**" 경로에 매칭되는 API를 그룹화하여 문서화 한다
		String[] paths={ "/api/v1/**", "/api/auth/**" };
		return GroupedOpenApi.builder()
				// Group Name
				.group("Tool Controller API v1")
				// Define Path pattern which included in group
				.pathsToMatch(paths)
				.build();
	}
}
