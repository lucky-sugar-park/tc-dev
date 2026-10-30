package com.wow.be.tc.endpoints;

import java.io.IOException;
import java.util.Date;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import com.wow.be.framework.security.ClientAddressResolver;
import com.wow.be.framework.security.authz.PublicApi;
import com.wow.be.tc.auth.AuthService;
import com.wow.be.tc.auth.LoginRequest;
import com.wow.be.tc.auth.LoginResponse;
import com.wow.be.tc.auth.SecurityUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 / 토큰 재발급 / 로그아웃.
 *
 * <p>framework SecurityConfig 가 {@code POST /api/auth/login}·{@code /api/auth/reissue} 만 공개하므로 경로를 {@code /auth} 에서
 * {@code /api/auth} 로 옮겼다. refresh token 쿠키 경로도 {@code /api/auth} 다. 접속 주소는 framework JwtFilter 와 같은 규칙
 * ({@link ClientAddressResolver})으로 구해야 토큰의 주소 묶음 검증이 맞는다.
 */
@Tag(name="AuthController", description="API for User Login / Reissue / Logout")
@RestController
@RequiredArgsConstructor
@RequestMapping(value="/api/auth")
@Slf4j
public class AuthController {

	private static final String KEY_REFRESH_TOKEN = "refreshToken";
	private static final String KEY_AUTHORIZATION = "Authorization";
	private static final String PREFIX_BEARER = "Bearer ";
	/** refresh token 쿠키를 붙일 경로 — 이 컨트롤러의 모든 URI */
	private static final String COOKIE_PATH = "/api/auth";

	private final AuthService authService;

	private final ClientAddressResolver clientAddressResolver;

	@PostMapping("/login")
	@PublicApi
	public LoginResponse login(@RequestBody @Validated LoginRequest loginRequest, HttpServletRequest request,  HttpServletResponse response) {
		log.debug("");

		loginRequest.setRemoteAddress(extractRemoteAddress(request));
		SecurityUser suser = authService.login(loginRequest);
		int maxAge=(int)suser.getJwTokenInfo().getRefreshTokenExpiredTime().getTime()/1000-(int)new Date().getTime()/1000;

		Cookie cookie = new Cookie(KEY_REFRESH_TOKEN, suser.getJwTokenInfo().getRefreshToken());
		cookie.setMaxAge(maxAge);
		// /api/auth 로 시작되는 모든 uri에 대해서 이 쿠키를 붙임
		cookie.setPath(COOKIE_PATH);
		// java script로 handling 하지 못하도록 하는 옵션
		cookie.setHttpOnly(true);
		// 서로 다른 사이트 (SPA와 어플리케이션은 다른 사이트임)라도 이 쿠키를 적용함
		cookie.setAttribute("SameSite", "Lax");
		response.addCookie(cookie);

		response.setStatus(HttpServletResponse.SC_OK);
		response.setHeader(KEY_AUTHORIZATION, PREFIX_BEARER+suser.getJwTokenInfo().getAccessToken());
		// 자바스크립트로 가져갈 수 있도록, 웹 브라우저에서 Authorization을 노출시킴
		response.setHeader("Access-Control-Expose-Headers", "Authorization");

		return new LoginResponse(true, suser.getUserInfo());
	}

	@PostMapping("/reissue")
	@PublicApi
	public ResponseEntity<Void> reissueAccessToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
		log.debug("reissue AccessToken start");

		String remoteAddress = extractRemoteAddress(request);
		Cookie cookie = WebUtils.getCookie(request, KEY_REFRESH_TOKEN);

		SecurityUser suser = authService.reissue(remoteAddress, cookie.getValue());

		int maxAge=(int)suser.getJwTokenInfo().getRefreshTokenExpiredTime().getTime()/1000-(int)new Date().getTime()/1000;
		cookie = new Cookie(KEY_REFRESH_TOKEN, suser.getJwTokenInfo().getRefreshToken());
		cookie.setMaxAge(maxAge);
		// /api/auth 로 시작되는 모든 uri에 대해서 이 쿠키를 붙임
		cookie.setPath(COOKIE_PATH);
		// 자바스크립트에서는 쿠키를 접근할 수 없음
		cookie.setHttpOnly(true);
		// 서로 다른 사이트 (SPA와 어플리케이션은 다른 사이트임)라도 이 쿠키를 적용함
		cookie.setAttribute("SameSite", "Lax");
		response.addCookie(cookie);

		response.setStatus(HttpServletResponse.SC_OK);
		response.setHeader(KEY_AUTHORIZATION, PREFIX_BEARER+suser.getJwTokenInfo().getAccessToken());
		// 자바스크립트로 가져갈 수 있도록, 웹 브라우저에서 Authorization을 노출시킴
		response.setHeader("Access-Control-Expose-Headers", "Authorization");

		return ResponseEntity.ok().build();
	}

	@Operation(
			summary="Request for logout",
			description="Request for logout"
	)
	@ApiResponse(
			responseCode="200",
			description="Successful to logout"
	)
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		log.debug("");

		String remoteAddress = extractRemoteAddress(request);
		String refreshToken = findCookie(request).getValue();

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		authService.logout(authentication.getName(), remoteAddress, refreshToken);
		SecurityContextHolder.clearContext();
		// remove cookie
		Cookie cookie = new Cookie(KEY_REFRESH_TOKEN, "");
		cookie.setMaxAge(0);
		// 아래 옵션을 줘야 쿠키가 지워짐
		cookie.setAttribute("SameSite", "Lax");
		response.addCookie(cookie);

		return ResponseEntity.ok().build();
	}

	private Cookie findCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		for(Cookie cookie:cookies) {
			if(KEY_REFRESH_TOKEN.equals(cookie.getName())) return cookie;
		}
		return null;
	}

	private String extractRemoteAddress(HttpServletRequest request) {
		return clientAddressResolver.resolve(request);
	}
}
