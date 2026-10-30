# framework-core 0.8.46 수정 명세 — SearchSpec, JwtFilter, SecurityConfig

> 작성: 2026-10-30. 대상: `mes-dev/wow-mes-backend/wow-mes-backend-framework-core`, 패키지 `com.wow.be.framework`.
> framework-core 는 사용자가 직접 고쳐 Nexus 에 배포한다. 이번부터 버전을 올려 **0.8.46** 으로 낸다(같은 버전 재배포 금지).
> 관련: [backend-todo.md](backend-todo.md) #8, 이전 명세 [framework-core-dispatch-changes.md](framework-core-dispatch-changes.md).

## 0. 요약

| # | 대상 | 문제 | 바꿀 것 |
|---|---|---|---|
| 1 | `persist/SearchSpec.createSpecification` | conjType 이 null 이면 NPE. 없는 항목·조건 생성 실패를 **조용히 건너뛰어 걸러지지 않은 전체 행**이 나감 | null 안전, AND 기본, 실패는 예외 |
| 2 | `security/JwtConfig`(JwtFilter 빈) | Spring Boot 가 서블릿 필터로도 자동 등록 → **모든 요청에서 토큰을 두 번 검증**, 앱이 따로 둔 보안 체인의 요청을 401 로 막음 | 서블릿 자동 등록 끄기 |
| 3 | `security/SecurityConfig`, `JwtFilter.shouldNotFilter` | MES 전용 공개 경로가 고정 목록으로 모든 앱에 열림. **`/actuator/**` 전체 공개**. 같은 목록이 두 곳에 하드코딩 | 공개 경로를 설정(`app.security.*`)으로, 한 목록을 두 곳이 같이 씀 |

MES 영향: 1은 잘못된 검색 요청이 500/전체 행 대신 400 이 된다. 2는 동작 변화 없음(검증 1회로 줄어듦). 3은 기본값이 지금 목록과 같으므로 `/api/**` 동작은 같고, **actuator 공개가 `/actuator/health` 로 좁혀진다** — MES 가 다른 actuator 엔드포인트를 인증 없이 쓰는지 배포 전에 확인한다.

---

## 1. SearchSpec — `persist/SearchSpec.java`

### 1.1 지금 코드의 문제

```java
Specification<T> spec=Specification.unrestricted();
for(CondItem con : cond.getConditions()) {            // ① conditions 가 null 이면 NPE
    ...
    method=MethodUtils.findMethod(this.getClass(), "with", pname);
    if(method==null) continue;                          // ② 없는 항목은 조용히 건너뜀
    try {
        if(spec==null) { ... continue; }                // ③ spec 이 null 일 수 없어 죽은 코드
        switch(con.getConjType()) {                     // ④ conjType 이 null 이면 NPE
        ...
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        e.printStackTrace();                            // ⑤ 조건 생성 실패를 삼킴
    }
}
```

- ④ 때문에 tc 는 `utils/SearchSpecs` 우회 클래스를 두고 있다(화면이 첫 조건의 conjType 을 보내지 않는 경우가 많다).
- **②⑤가 더 위험하다.** 항목 이름 오타·값 형 오류가 있으면 그 조건이 빠진 채 검색되어, 걸러진 결과를 기대한 사용자에게 전체 행이 나간다.
- `Condition.toString()`(75행)의 `cond.getConjType().name()` 도 null 이면 NPE 다(로그를 찍다 터진다).

### 1.2 바꿀 코드

```java
@SuppressWarnings("unchecked")
public Specification<T> createSpecification(Condition cond) {
    Specification<T> spec = Specification.unrestricted();
    if (cond == null || cond.getConditions() == null) {
        return spec;
    }
    boolean first = true;
    for (CondItem con : cond.getConditions()) {
        if (con == null || con.getName() == null || con.getName().isBlank()) {
            continue;
        }
        String pname = con.getName();
        if (con.getCondType() == CondType.IN || con.getCondType() == CondType.NOT_IN) {
            pname += "s";
        }
        Method method = MethodUtils.findMethod(this.getClass(), "with", pname);
        if (method == null) {
            // 조건을 빼고 검색하면 걸러지지 않은 전체 행이 나간다 — 조용히 넘기지 않는다
            throw new IllegalArgumentException("검색할 수 없는 항목입니다: " + con.getName());
        }
        Specification<T> item;
        try {
            item = (Specification<T>) method.invoke(this, con.getCondType(), con.getValue());
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("검색 조건이 잘못되었습니다: " + con.getName(), e.getCause());
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalArgumentException("검색 조건이 잘못되었습니다: " + con.getName(), e);
        }
        if (item == null) {
            continue;
        }
        // conjType 이 없으면 AND. 첫 조건의 conjType 은 의미가 없으므로 무시한다
        ConjType conj = con.getConjType() == null ? ConjType.AND : con.getConjType();
        spec = (first || conj == ConjType.AND) ? spec.and(item) : spec.or(item);
        first = false;
    }
    return spec;
}
```

- import 추가: `com.wow.be.framework.Condition.ConjType`
- `Condition.toString()` 75행: `cond.getConjType().name()` → `String.valueOf(cond.getConjType())`

### 1.3 결정할 점

- **없는 항목에 예외**를 던지면, 화면이 원래 여분의 항목을 보내던 앱에서는 400 이 나기 시작한다. 영향이 걱정되면 한 버전 동안 `log.warn` 후 건너뛰기로 두고 MES 로그로 발생 여부를 본 뒤 예외로 바꾸는 단계적 방법도 있다. **권장은 예외** — 조용히 전체 행을 돌려주는 쪽이 더 나쁘다.
- 예외는 `IllegalArgumentException` 이므로 각 앱의 전역 예외 처리기가 400 으로 매핑하는지 확인한다(tc 는 지금 500 — §4 에서 맞춘다).

---

## 2. JwtFilter 서블릿 이중 등록 — `security/JwtConfig.java`

### 2.1 문제

- `JwtConfig` 가 `JwtFilter` 를 `@Bean` 으로 만든다. Spring Boot 는 `Filter` 타입 빈을 **서블릿 필터로도 자동 등록**한다. mes-dev 전체에 `FilterRegistrationBean` 이 없으므로 **MES 도 같은 상태**다.
- `OncePerRequestFilter` 의 중복 방지 표시가 막지 못한다. 보안 체인 안에서는 필터 이름이 없어 클래스 이름으로, 서블릿 단계에서는 빈 이름(`jwtFilter`)으로 표시해 서로 다른 것으로 본다.
- 결과
  - 모든 `/api/**` 요청이 **토큰 검증(Redis 조회 포함)을 두 번** 한다.
  - 앱이 보안 체인을 따로 두면 서블릿 단계의 JwtFilter 가 그 요청을 401 `EXPIRED_TOKEN` 으로 막는다. tc 의 API 키 체인에서 실제로 확인했다(docs/auth-and-audit-design.md §6.7). tc 는 지금 자체적으로 등록을 끄고 있다.

### 2.2 바꿀 코드 — `JwtConfig` 에 빈 하나 추가

```java
/**
 * JwtFilter 는 SecurityConfig 의 보안 체인에서만 돈다. @Bean 이라 Spring Boot 가 서블릿 필터로도 자동 등록하므로 그것을 끈다
 * — 켜 두면 모든 요청에서 토큰을 두 번 검증하고, 앱이 따로 둔 보안 체인의 요청까지 401 로 막는다.
 */
@Bean
public FilterRegistrationBean<JwtFilter> jwtFilterServletRegistration(JwtFilter jwtFilter) {
    FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
    registration.setEnabled(false);
    return registration;
}
```

- import: `org.springframework.boot.web.servlet.FilterRegistrationBean`
- 다른 방법: `JwtFilter` 를 빈으로 두지 않고 `SecurityConfig.filterChain` 안에서 `new` 로 만든다. `JwtFilter` 를 주입받는 코드를 바꿔야 하므로 위 방법이 변경 범위가 가장 작다.

### 2.3 함께 정리할 것(선택)

| 대상 | 문제 | 처리 |
|---|---|---|
| `JwtSecurityConfig` | 아무도 쓰지 않는다. 여기서도 `addFilterBefore(jwtFilter)` 를 해서, 누가 적용하면 세 번째 실행이 생긴다 | 삭제 |
| `JwtFilter.doFilterInternal` 의 `case "/auth/reissue"`, `"/auth/login"` | 경로가 `/api/auth/...` 로 바뀐 뒤 맞는 경우가 없는 죽은 코드. 맞으면 `resolveRefreshToken` 이 쿠키가 없을 때 NPE | 삭제 |
| `noJwtRequiredList`(`app.authorization.permits`), `AuthorizationConfig` | mes-dev·tc 어디서도 설정하지 않는다. 비교 루프는 `**` 다음 글자를 범위 밖에서 읽을 수 있다 | §3 의 공개 경로 목록으로 대체하고 삭제 |

---

## 3. 공개 경로를 설정으로 — `security/SecurityConfig.java`, `JwtFilter`, `JwtConfig`, 새 `PublicPathsProperties`

### 3.1 문제

- `SecurityConfig.filterChain` 이 MES 화면 기준의 공개 경로를 **고정 목록**으로 연다 — 회원가입·비밀번호 재설정·이메일 인증, `/mobile/**`, `GET /api/i18n/**`, `/actuator/**`(메서드 무관). tc 에는 이 기능이 없는데도 열린다. 지금은 404 라 해가 없지만 **나중에 tc 가 같은 경로에 API 를 만들면 인증 없이 열린다.**
- **`/actuator/**` 전체 공개는 MES 에도 위험하다.** 노출 엔드포인트를 늘리는 순간 `/actuator/env`, `/actuator/heapdump` 가 인증 없이 나간다.
- 같은 목록이 `JwtFilter.shouldNotFilter` 에 한 벌 더 하드코딩되어 있어 손으로 맞춰야 한다.
- 앱 고유의 공개 경로(tc 의 `/api/health`)를 더할 방법이 없어 tc 는 체인을 따로 만들었다.

### 3.2 새 파일 `security/PublicPathsProperties.java`

```java
package com.wow.be.framework.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import lombok.Data;

/**
 * 인증 없이 받는 경로 ({@code app.security.*}). SecurityConfig 의 permitAll 과 JwtFilter 의 검증 건너뛰기가 같은 목록을 쓴다.
 *
 * <pre>
 * app:
 *   security:
 *     public-paths:            # "METHOD 패턴" 또는 "패턴"(메서드 무관)
 *       - POST /api/auth/login
 *       - GET /api/health
 *     swagger-public: false
 *     actuator-public: [/actuator/health]
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class PublicPathsProperties {

	private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

	/** 기본값은 지금 MES 목록 그대로 — MES 는 설정 없이 지금처럼 동작한다 */
	private List<String> publicPaths = new ArrayList<>(List.of(
		"POST /api/auth/login", "POST /api/auth/reissue",
		"POST /api/auth/signup", "POST /api/auth/password-reset/request", "POST /api/auth/password-reset/confirm",
		"GET /api/auth/verify", "POST /api/auth/resend-verification", "GET /api/auth/email-change/confirm",
		"GET /api/i18n/**", "HEAD /api/i18n/**", "GET /mobile", "GET /mobile/**"));

	/** Swagger(OpenAPI) 공개 — 운영에서는 false 권장 */
	private boolean swaggerPublic = true;

	/** 공개할 actuator 경로. 기본은 health 만(예전처럼 /actuator/** 전체를 열지 않는다) */
	private List<String> actuatorPublic = new ArrayList<>(List.of("/actuator/health", "/actuator/health/**"));

	/**
	 * 위 설정 전부를 RequestMatcher 목록으로 바꾼다. 형식이 틀린 항목은 기동 때 바로 실패시킨다
	 * — 공개 경로를 잘못 적은 채 뜨면 의도와 다르게 열리거나 막힌다.
	 */
	public List<RequestMatcher> toMatchers(PathPatternRequestMatcher.Builder mvc) {
		List<RequestMatcher> matchers = new ArrayList<>();
		for (String entry : publicPaths) {
			matchers.add(parse(mvc, entry));
		}
		if (swaggerPublic) {
			// "/v3/api-docs/**" 는 "/v3/api-docs" 자체를 매치하지 않으므로 둘 다 둔다(지금 SecurityConfig 와 같음)
			for (String p : List.of("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")) {
				matchers.add(mvc.matcher(p));
			}
		}
		for (String p : actuatorPublic) {
			matchers.add(mvc.matcher(p.trim()));
		}
		return List.copyOf(matchers);
	}

	/** "POST /api/x" → POST 만, "/api/x" → 메서드 무관 */
	private static RequestMatcher parse(PathPatternRequestMatcher.Builder mvc, String entry) {
		String e = entry == null ? "" : entry.trim();
		if (e.isEmpty()) {
			throw new IllegalStateException("app.security.public-paths 에 빈 항목이 있습니다");
		}
		int space = e.indexOf(' ');
		if (space < 0) {
			return mvc.matcher(requirePath(e, entry));
		}
		String method = e.substring(0, space).toUpperCase(Locale.ROOT);
		String path = e.substring(space + 1).trim();
		if (!METHODS.contains(method)) {
			throw new IllegalStateException("app.security.public-paths 의 메서드가 잘못되었습니다: " + entry);
		}
		return mvc.matcher(HttpMethod.valueOf(method), requirePath(path, entry));
	}

	private static String requirePath(String path, String entry) {
		if (!path.startsWith("/")) {
			throw new IllegalStateException("app.security.public-paths 의 경로는 / 로 시작합니다: " + entry);
		}
		return path;
	}
}
```

### 3.3 `JwtConfig` — 설정 등록, JwtFilter 에 같은 목록 전달

```java
@Configuration
@EnableConfigurationProperties(PublicPathsProperties.class)   // 추가
public class JwtConfig {
	...
	@Bean
	public JwtFilter jwtFilter(JwTokenProvider tokenProvider, ClientAddressResolver clientAddressResolver,
			PublicPathsProperties publicPaths) {
		return new JwtFilter(tokenProvider, clientAddressResolver,
			publicPaths.toMatchers(PathPatternRequestMatcher.withDefaults()));
	}

	// §2.2 의 jwtFilterServletRegistration 도 이 클래스에 둔다
}
```

### 3.4 `JwtFilter` — 생성자와 `shouldNotFilter`

`AuthorizationConfig`·`noJwtRequiredList`·`passAllRequest` 를 지우고 matcher 목록을 받는다.

```java
private final List<RequestMatcher> publicMatchers;

public JwtFilter(JwTokenProvider jwtTokenProvider, ClientAddressResolver clientAddressResolver,
		List<RequestMatcher> publicMatchers) {
	this.jwtTokenProvider = jwtTokenProvider;
	this.clientAddressResolver = clientAddressResolver;
	this.publicMatchers = publicMatchers;
	this.objectMapper = new ObjectMapper();
}

@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
	String uri = request.getRequestURI();
	if ("GET".equalsIgnoreCase(request.getMethod()) && !uri.startsWith("/api/")) return true;  // SPA·정적 자원
	if (uri.startsWith("/api/sse/") && !uri.startsWith("/api/sse/event/")) return true;       // SSE 연결
	return publicMatchers.stream().anyMatch(m -> m.matches(request));
}
```

`doFilterInternal` 의 `default:` 분기 끝 `noJwtRequiredList` 비교 루프도 지운다. 공개 경로는 이미 `shouldNotFilter` 에서 걸러지므로 남는 조건은 `if (reissueRequired) { …401 EXPIRED_TOKEN… }` 하나다.

### 3.5 `SecurityConfig.filterChain` — 고정 목록 대신

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter, PublicPathsProperties publicPaths,
		JwtAuthenticationEntryPoint authenticationEntryPoint, JwtAccessDeniedHandler accessDeniedHandler) throws Exception {
	PathPatternRequestMatcher.Builder mvc = PathPatternRequestMatcher.withDefaults();
	RequestMatcher[] publics = publicPaths.toMatchers(mvc).toArray(RequestMatcher[]::new);
	http
		...
		.authorizeHttpRequests(auth -> auth
			.requestMatchers(mvc.matcher(HttpMethod.OPTIONS, "/**")).permitAll()
			.requestMatchers(publics).permitAll()
			// 정적 SPA 자원(GET /, /index.html, /*.js …)과 SSE 규칙은 지금 그대로 둔다
			...
```

지금 있는 인증 엔드포인트·이메일 인증·Swagger·Actuator·`/mobile`·`GET /api/i18n/**` 블록 다섯 개가 `.requestMatchers(publics).permitAll()` 한 줄로 바뀐다.

### 3.6 확인할 점

- **i18n 의 HEAD**: 지금 `JwtFilter` 는 `/api/i18n/**` 에 GET·HEAD 모두 검증을 건너뛰는데 `SecurityConfig` 는 GET 만 공개한다. 기본 목록에 `HEAD /api/i18n/**` 를 넣어 맞췄다. MES 에 HEAD 를 공개할 이유가 없으면 그 줄을 뺀다.
- **AuthorizationConfig 제거**: `app.authorization.permits` 를 mes-dev·tc 어디서도 설정하지 않는 것을 확인했다. 다른 앱이 이 framework 를 쓴다면 그 앱도 확인한다.
- **actuator**: MES 운영 설정과 모니터링(예: prometheus)이 다른 actuator 엔드포인트를 인증 없이 부르는지 확인하고, 그렇다면 `app.security.actuator-public` 에 더한다.
- **단위 테스트(권장)**: `toMatchers` 에 대해 `MockHttpServletRequest` 로 "POST 허용, 같은 경로 GET 은 아님, 잘못된 메서드면 기동 실패(IllegalStateException)" 를 확인한다.

---

## 4. 0.8.46 배포 뒤 tc 에서 할 일

| tc 코드 | 처리 |
|---|---|
| `wow-tc-backend/build.gradle` | `frameworkCoreVersion = '0.8.46'` |
| `utils/SearchSpecs`(conjType 우회, null → unrestricted) | `specs.createSpecification(cond)` 직접 호출로 되돌리고 클래스 삭제(서비스 11곳) |
| `endpoints/GlobalExceptionHandler` | `IllegalArgumentException` → 400(검색 항목·값 오류) |
| `security/TcSecurityConfig.jwtFilterServletRegistration` | 삭제(framework 가 처리) |
| `security/TcSecurityConfig.healthChain` | 삭제하고 공개 경로 설정에 `GET /api/health` |
| `application.yml` | `app.security.public-paths: [POST /api/auth/login, POST /api/auth/reissue, GET /api/health]`, `swagger-public`(운영은 false), `actuator-public: []`(tc 는 actuator 없음) |
| 확인 | `./gradlew :tc:test`, e2e 전체(`wow-tc-backend/e2e/run_all.py --shutdown`). 특히 `test_api_key`(JwtFilter), `test_shutdown` 의 헬스 체크, 검색이 있는 시험 |

Gradle 캐시: 버전을 올리므로 캐시 삭제는 필요 없다(같은 버전 재배포 때만 `~/.gradle/caches/modules-2/files-2.1/com.wow.be/wow-mes-backend-framework-core/<버전>` 을 지웠다).
