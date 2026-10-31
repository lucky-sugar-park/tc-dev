package com.wow.be.tc.ui;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.ClientAddressResolver;
import com.wow.be.framework.security.authz.AuthenticatedOnly;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.security.TcPermissionChecker;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 화면(MES shared 셸)이 부르는 본인 API (docs/frontend-design.md §4.1). 경로가 {@code /api/v1} 이 아니라 {@code /api} 인 것은
 * shared 의 계약이라서다. 비밀번호를 바꿔야 하는 사용자도 쓴다.
 */
@Tag(name = "UI — 본인", description = "화면 셸용: 권한·내 정보·비밀번호 변경")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@AuthenticatedOnly
public class MeUiController {

	private final UserService userService;

	private final ClientAddressResolver addressResolver;

	public record PasswordChangeRequest(String currentPassword, String newPassword) {
	}

	@Operation(summary = "내 권한", description = "{자원 코드: 동작 글자} — 역할 표 그대로 + 화면 권한 uiroute.tc.*. 관리자는 {\"*\": \"ACRUDX\"}")
	@GetMapping("/permissions")
	public Map<String, String> permissions() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
		return UiRoutePermissions.withScreens(TcPermissionChecker.effective(roles));
	}

	@Operation(summary = "내 정보")
	@GetMapping("/profile")
	public UserInfo profile() throws ToolControlException {
		return userService.findUserById(currentUserId());
	}

	@Operation(summary = "내 비밀번호 변경", description = "PUT /api/v1/me/password 와 같다 — shared 셸은 POST 로 부른다")
	@PostMapping("/password")
	public void changePassword(@RequestBody PasswordChangeRequest req, HttpServletRequest request) throws ToolControlException {
		userService.changeOwnPassword(currentUserId(), req.currentPassword(), req.newPassword(), addressResolver.resolve(request));
	}

	private static String currentUserId() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}
}
