package com.wow.be.tc.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * tc 사용자(운영자·관리자). docs/auth-and-audit-design.md §3.1.
 *
 * <p>가입 요청·승인 흐름과 직원 정보(전화번호·부가 정보), 로그인 "상태"(logined) 칼럼은 없앴다. 로그인 사실은 사용 이력
 * (USER_HIST)에 남는다.
 */
@Data
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
public class UserInfo extends AbstractInfo implements UserDetails {

	private static final long serialVersionUID = 1L;

	private String id;

	/** BCrypt 해시. 응답에 넣지 않는다 */
	@JsonIgnore
	private String password;

	private String name;

	/** 연락용(선택). 로그인·인증에는 쓰지 않는다 */
	private String email;

	private List<UserRole> userRoles = new ArrayList<>();

	/** false 면 로그인·재발급을 거부한다(사용 중지) */
	private boolean enabled = true;

	/** 관리자가 초기화한 뒤 첫 로그인에서 비밀번호 변경을 강제한다 */
	private boolean passwordMustChange;

	private Date passwordChangedAt;

	private Date lastLoginAt;

	private String lastLoginIp;

	public void addRole(String role) {
		userRoles.add(UserRole.toUserRole(role));
	}

	public boolean hasRole(UserRole role) {
		return userRoles != null && userRoles.contains(role);
	}

	@Override
	public String toPlainText() {
		return id + " [" + name + ", " + userRoles + (enabled ? "" : ", 중지") + "]";
	}

	@Override
	@JsonIgnore
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return userRoles.stream().map(role -> new SimpleGrantedAuthority(role.name())).collect(Collectors.toList());
	}

	@Override
	@JsonIgnore
	public String getUsername() {
		return id;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}
