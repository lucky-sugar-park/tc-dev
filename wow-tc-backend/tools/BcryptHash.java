import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 비밀번호의 BCrypt 해시를 만든다 — 최초 관리자(TC_BOOTSTRAP_ADMIN_PASSWORD_HASH)와 비상 복구용(docs/operations.md).
 * 비밀번호는 표준 입력으로 받는다(명령줄 인자는 셸 기록·프로세스 목록에 남는다).
 *
 * <pre>
 * java -cp "spring-security-crypto-7.x.jar;commons-logging-1.x.jar" BcryptHash.java
 *   (Linux·macOS 는 ; 대신 :)
 * </pre>
 * jar 는 Gradle 캐시(~/.gradle/caches/modules-2/files-2.1/org.springframework.security/spring-security-crypto)나
 * tc 실행 jar 의 BOOT-INF/lib 에 있다.
 */
public class BcryptHash {

	public static void main(String[] args) throws Exception {
		System.err.print("비밀번호: ");
		String password;
		if (System.console() != null) {
			password = new String(System.console().readPassword());
		} else {
			password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
		}
		if (password == null || password.length() < 10) {
			System.err.println("10자 이상이어야 합니다.");
			System.exit(1);
		}
		System.out.println(new BCryptPasswordEncoder().encode(password));
	}
}
