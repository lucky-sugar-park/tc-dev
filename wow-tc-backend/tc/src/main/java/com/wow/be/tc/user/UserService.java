package com.wow.be.tc.user;

import java.util.List;
import java.util.Optional;

import com.wow.be.tc.ToolControlException;

/**
 * 사용자 관리 (docs/auth-and-audit-design.md §3). 모든 변경은 재기동 없이 바로 반영된다.
 *
 * <p>보호 규칙: 마지막 활성 관리자는 중지·삭제·관리자 역할 제거를 할 수 없다. 자기 자신은 중지·삭제할 수 없다.
 * 사용 중지·삭제·비밀번호 초기화 때는 그 사용자의 토큰을 모두 지워 바로 끊는다.
 */
public interface UserService {

	/** 사용 이력(USER_HIST) 한 줄 — LOGIN, LOGIN_FAIL, LOGIN_THROTTLED, LOGOUT, REISSUE_DENIED, PASSWORD_CHANGE 등 */
	void recordUserHistory(String userId, String oper, String ipAddress, String remark);

	Optional<UserInfo> findUser(String id);

	/** 없으면 ERR_404 */
	UserInfo findUserById(String id) throws ToolControlException;

	List<UserInfo> searchUsers();

	/** 생성. 초기 비밀번호는 관리자가 정하고, 첫 로그인에서 바꾸게 한다(PASSWORD_MUST_CHANGE). */
	UserInfo createUser(UserInfo info, String initialPassword) throws ToolControlException;

	/** 이름·이메일·역할 변경 */
	UserInfo updateUser(UserInfo info) throws ToolControlException;

	UserInfo setEnabled(String id, boolean enabled) throws ToolControlException;

	/** 관리자의 비밀번호 초기화 — 첫 로그인에서 바꾸게 하고, 그 사용자의 토큰을 모두 지운다 */
	void resetPassword(String id, String newPassword) throws ToolControlException;

	void deleteUser(String id) throws ToolControlException;

	/** 본인 비밀번호 변경 — 현재 비밀번호를 확인한다 */
	void changeOwnPassword(String id, String currentPassword, String newPassword, String ipAddress) throws ToolControlException;

	/** 로그인 성공 — 마지막 로그인 시각·주소와 사용 이력 */
	void recordLoginSuccess(String id, String ipAddress);

	/** 비밀번호를 바꿔야 해서 다른 API 를 막아야 하는가(짧게 캐시한다) */
	boolean mustChangePassword(String id);
}
