package com.wow.be.tc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import lombok.extern.slf4j.Slf4j;

/**
 * Tool Control(tc) 실행 진입점 (:8080).
 *
 * <p>컴포넌트 스캔은 tc 패키지와, framework-core 중 tc 가 쓰는 패키지(cache·persist·security·session)만 연다.
 * <ul>
 *   <li>framework {@code dispatch} 의 MessageDispatcher 를 쓴다. tc 는 커맨드 찾기({@code InterfaceCommandResolver}),
 *       기본 커맨드({@code PlcInterfaceCommand}), 감사 기록({@code AuditDispatchInterceptor})만 더한다.</li>
 *   <li>framework {@code sse.ServerSideEventController}(/api/sse/**) 대신 tc 의 {@code /connect/{userId}} 를 유지한다.</li>
 *   <li>Redis 데모 빈(RedisTestService/RedisTestRepository)과 JasyptConfig(ENC 값을 쓰지 않음, 마스터키 없으면 기동 실패)는 뺀다.</li>
 * </ul>
 *
 * <p>Redis 리포지터리(*RedisCacheRepository, *RefreshTokenRepository)는 framework {@code RedisConfig} 의
 * {@code @EnableRedisRepositories} 가 따로 올리므로 JPA 리포지터리 스캔에서는 제외한다.
 */
@SpringBootApplication
@ComponentScan(
	basePackages = {
		"com.wow.be.tc",
		"com.wow.be.framework.cache",
		"com.wow.be.framework.dispatch",
		"com.wow.be.framework.persist",
		"com.wow.be.framework.security",
		"com.wow.be.framework.session"
	},
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.REGEX,
		pattern = {
			".*RedisTestService",
			".*RedisTestRepository",
			"com\\.wow\\.be\\.framework\\.security\\.JasyptConfig"
		}
	)
)
@EntityScan(basePackages = "com.wow.be.tc")
@EnableJpaRepositories(
	basePackages = "com.wow.be.tc",
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.REGEX,
		pattern = {
			".*RedisCacheRepository",
			".*RefreshTokenRepository"
		}
	)
)
@Slf4j
public class ToolControlApplication {

	public static void main(String[] args) {
		log.debug("ToolControlApplication is starting...");
		SpringApplication.run(ToolControlApplication.class, args);
	}
}
