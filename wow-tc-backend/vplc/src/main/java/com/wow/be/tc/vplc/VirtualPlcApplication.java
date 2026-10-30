package com.wow.be.tc.vplc;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.wow.be.framework.persist.HikariLoader;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 PLC 시뮬레이터 실행 진입점 (:8088).
 *
 * <p>컴포넌트 스캔은 이 모듈 패키지({@code com.wow.be.tc.vplc})로만 한정한다. framework-core 는 클래스만 가져다 쓰고
 * 빈은 스캔하지 않는다 — 특히 framework 의 JWT 보안 설정(SecurityConfig·JwtSecurityConfig·JwtFilter 등),
 * Redis 데모 빈(RedisTestService), 권한 인터셉터(AuthzWebConfig), framework SSE 컨트롤러(/api/sse/**),
 * PermissionAuditController 가 올라오지 않는다. vplc 는 시험용 시뮬레이터라 레거시처럼 인증 없이 연다
 * (보안 설정은 {@code security.SecurityConfig}).
 *
 * <p>framework 빈 중 필요한 것만 {@link Import} 로 올린다: 기동 때 DB 커넥션을 미리 여는 {@link HikariLoader}.
 */
@SpringBootApplication(scanBasePackages = "com.wow.be.tc.vplc")
@Import(HikariLoader.class)
@Slf4j
public class VirtualPlcApplication implements CommandLineRunner {

	public static void main( String[] args ) {
		SpringApplication.run(VirtualPlcApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		StringBuilder sb=new StringBuilder();
		sb.append("##    ##  ##  #####    ########  ##   ##    ###    ##         #####    ##         #####\n");
		sb.append("##    ##  ##  ##   ##     ##     ##   ##  ##   ##  ##         ##   ##  ##       ##     \n");
		sb.append("##    ##  ##  ##   ##     ##     ##   ##  ##   ##  ##         ##   ##  ##       ##     \n");
		sb.append("##    ##  ##  #####       ##     ##   ##  #######  ##         #####    ##       ##     \n");
		sb.append("##    ##  ##  ##   ##     ##     ##   ##  ##   ##  ##         ##       ##       ##     \n");
		sb.append(" ##  ##   ##  ##   ##     ##     ##   ##  ##   ##  ##      ## ##       ##       ##     \n");
		sb.append("   ##     ##  ##   ##     ##      #####   ##   ##  ####### ## ##       #######    #####\n");

		log.info("Virtual PLC is started...\n{}", sb.toString());
	}
}
