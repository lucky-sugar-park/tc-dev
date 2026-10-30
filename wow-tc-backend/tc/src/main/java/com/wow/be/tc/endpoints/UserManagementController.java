package com.wow.be.tc.endpoints;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserRole;
import com.wow.be.tc.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 관리 API (docs/auth-and-audit-design.md §3.3). 관리자 전용(자원 tc.user). 재기동 없이 바로 반영된다.
 */
@Tag(name = "User", description = "사용자 관리 — 관리자 전용")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Requires("tc.user")
public class UserManagementController {

	private final UserService userService;

	/** 생성 요청. 초기 비밀번호는 첫 로그인에서 바꾸게 한다 */
	public record CreateUserRequest(String id, String name, String email, List<UserRole> userRoles, String initialPassword) {
	}

	/** 수정 요청(이름·이메일·역할) */
	public record UpdateUserRequest(String name, String email, List<UserRole> userRoles) {
	}

	public record PasswordResetRequest(String newPassword) {
	}

	@Operation(summary = "사용자 목록")
	@Requires(action = 'R')
	@GetMapping
	public List<UserInfo> search() {
		return userService.searchUsers();
	}

	@Operation(summary = "사용자 한 명", description = "비밀번호 해시는 주지 않는다.")
	@Requires(action = 'R')
	@GetMapping("/{id}")
	public UserInfo find(@PathVariable("id") String id) throws ToolControlException {
		return userService.findUserById(id);
	}

	@Operation(summary = "사용자 생성", description = "초기 비밀번호는 10자 이상. 첫 로그인에서 바꿔야 한다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserInfo create(@RequestBody CreateUserRequest req) throws ToolControlException {
		return userService.createUser(toInfo(req.id(), req.name(), req.email(), req.userRoles()), req.initialPassword());
	}

	@Operation(summary = "사용자 수정", description = "이름·이메일·역할. 마지막 활성 관리자의 관리자 역할은 뺄 수 없다.")
	@PutMapping("/{id}")
	public UserInfo update(@PathVariable("id") String id, @RequestBody UpdateUserRequest req) throws ToolControlException {
		return userService.updateUser(toInfo(id, req.name(), req.email(), req.userRoles()));
	}

	@Operation(summary = "사용 재개")
	@PostMapping("/{id}/enable")
	public UserInfo enable(@PathVariable("id") String id) throws ToolControlException {
		return userService.setEnabled(id, true);
	}

	@Operation(summary = "사용 중지", description = "그 사용자의 토큰을 모두 지워 바로 끊는다. 자기 자신·마지막 활성 관리자는 안 된다.")
	@PostMapping("/{id}/disable")
	public UserInfo disable(@PathVariable("id") String id) throws ToolControlException {
		return userService.setEnabled(id, false);
	}

	@Operation(summary = "비밀번호 초기화", description = "첫 로그인에서 바꾸게 하고, 그 사용자의 토큰을 모두 지운다.")
	@PostMapping("/{id}/password-reset")
	public void resetPassword(@PathVariable("id") String id, @RequestBody PasswordResetRequest req) throws ToolControlException {
		userService.resetPassword(id, req.newPassword());
	}

	@Operation(summary = "사용자 삭제", description = "이력은 아이디 문자열로 남는다. 운영에서는 사용 중지를 권장한다.")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("id") String id) throws ToolControlException {
		userService.deleteUser(id);
	}

	private static UserInfo toInfo(String id, String name, String email, List<UserRole> roles) {
		UserInfo info = new UserInfo();
		info.setId(id);
		info.setName(name);
		info.setEmail(email);
		if (roles != null) {
			info.getUserRoles().addAll(roles);
		}
		return info;
	}
}
