package com.wow.be.tc.endpoints;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.ClientAddressResolver;
import com.wow.be.framework.security.authz.AuthenticatedOnly;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 본인 정보와 비밀번호 변경 (docs/auth-and-audit-design.md §3.3). 로그인한 사용자 누구나. 비밀번호를 바꿔야 하는 사용자도
 * 이 API 는 쓸 수 있다.
 */
@Tag(name = "My Account", description = "내 정보·비밀번호 변경")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@AuthenticatedOnly
public class MyAccountController {

	private final UserService userService;

	private final ClientAddressResolver addressResolver;

	public record PasswordChangeRequest(String currentPassword, String newPassword) {
	}

	@Operation(summary = "내 정보", description = "역할과 비밀번호 변경 필요 여부(passwordMustChange)를 함께 준다.")
	@GetMapping
	public UserInfo me() throws ToolControlException {
		return userService.findUserById(currentUserId());
	}

	@Operation(summary = "내 비밀번호 변경", description = "현재 비밀번호를 확인한다. 10자 이상, 아이디·지금 비밀번호와 달라야 한다.")
	@PutMapping("/password")
	public void changePassword(@RequestBody PasswordChangeRequest req, HttpServletRequest request) throws ToolControlException {
		userService.changeOwnPassword(currentUserId(), req.currentPassword(), req.newPassword(), addressResolver.resolve(request));
	}

	private static String currentUserId() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}
}
