package com.wow.be.tc.user.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 사용자 (USER). 역할은 쉼표로 이어 한 칼럼에 둔다.
 *
 * <p>예전 칼럼(phone, extra_info, logined, logined_ip)은 쓰지 않는다 — migration/005_user_cleanup.sql 참고.
 */
@Entity
@Table(name = "USER")
@Data
@ToString(callSuper = true, exclude = "password")
@EqualsAndHashCode(callSuper = false)
public class UserEntity extends PersistentEntity<UserInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "ID")
	private String id;

	@Column(name = "password")
	private String password;

	@Column(name = "name")
	private String name;

	@Column(name = "email")
	private String email;

	@Column(name = "roles")
	private String roles;

	/** 예전 행은 NULL 일 수 있다 — 사용 중으로 본다(예전 코드는 이 값을 검사하지 않았다) */
	@Column(name = "enabled")
	private Boolean enabled;

	@Column(name = "password_must_change")
	private Boolean passwordMustChange;

	@Column(name = "password_changed_at")
	private Date passwordChangedAt;

	@Column(name = "last_login_at")
	private Date lastLoginAt;

	@Column(name = "last_login_ip", length = 64)
	private String lastLoginIp;

	@Override
	public UserInfo info() {
		UserInfo info = new UserInfo();
		info.setId(id);
		info.setPassword(password);
		info.setName(name);
		info.setEmail(email);
		if (roles != null && !roles.isBlank()) {
			Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).forEach(info::addRole);
		}
		info.setEnabled(enabled == null || enabled);
		info.setPasswordMustChange(Boolean.TRUE.equals(passwordMustChange));
		info.setPasswordChangedAt(passwordChangedAt);
		info.setLastLoginAt(lastLoginAt);
		info.setLastLoginIp(lastLoginIp);
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	/** 화면에서 바꾸는 값(이름·이메일·역할)만 옮긴다. 비밀번호·사용 여부·로그인 기록은 서비스가 따로 바꾼다. */
	@Override
	public void from(UserInfo info) {
		this.name = info.getName();
		this.email = info.getEmail();
		this.roles = info.getUserRoles() == null ? null
			: info.getUserRoles().stream().filter(r -> r != UserRole.ROLE_UNKNOWN).map(UserRole::name).distinct()
				.collect(Collectors.joining(","));
	}

	@Override
	public String toPlainText() {
		return id;
	}
}
