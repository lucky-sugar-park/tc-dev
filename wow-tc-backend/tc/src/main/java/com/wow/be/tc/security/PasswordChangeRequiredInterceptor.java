package com.wow.be.tc.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 비밀번호를 바꿔야 하는 사용자(관리자 초기화·최초 관리자)는 본인 정보·비밀번호 변경·로그아웃 말고는 쓸 수 없게 한다
 * (docs/auth-and-audit-design.md §3.3). 로그인은 성공시키고, 그다음 API 에서 403 {@code ERR_403_TC_PASSWORD_CHANGE_REQUIRED}.
 * API 키 호출(타 시스템)에는 해당하지 않는다.
 */
@Configuration
@RequiredArgsConstructor
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final UserService userService;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this)
			.addPathPatterns("/api/**")
			.excludePathPatterns("/api/auth/**", "/api/v1/me", "/api/v1/me/**",
				// 화면 셸(docs/frontend-design.md §4.1) — 비밀번호를 바꿔야 하는 사용자도 셸을 띄우고 바꿀 수 있어야 한다
				"/api/me/permissions", "/api/me/profile", "/api/me/password", "/api/i18n/**", "/api/menus", "/api/ui-routes",
				"/api/operation-options/effective", "/api/sse/**");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
				|| auth instanceof ApiClientAuthentication) {
			return true;
		}
		if (userService.mustChangePassword(auth.getName())) {
			SecurityErrors.write(request, response, HttpServletResponse.SC_FORBIDDEN, ToolControlException.PASSWORD_CHANGE_REQUIRED,
				"비밀번호를 바꿔야 합니다. PUT /api/v1/me/password 로 바꾼 뒤 다시 시도하세요.");
			return false;
		}
		return true;
	}
}
