package com.wow.be.tc.user;

import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.user.impl.UserEntity;
import com.wow.be.tc.user.impl.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 최초 관리자 생성 (docs/auth-and-audit-design.md §3.2). <b>USER 표가 비어 있을 때만</b> 설정값으로 첫 관리자를 만든다.
 * 한 명이라도 있으면 설정을 읽지 않는다 — 설정을 바꿔도 아무 일이 일어나지 않으니 헷갈릴 여지가 없다.
 *
 * <pre>
 * tc.bootstrap.admin-id            : TC_BOOTSTRAP_ADMIN_ID
 * tc.bootstrap.admin-password-hash : TC_BOOTSTRAP_ADMIN_PASSWORD_HASH  (BCrypt 해시. 평문 금지)
 * </pre>
 * 만든 관리자는 첫 로그인에서 비밀번호를 바꿔야 한다. 평문 비밀번호를 만들어 로그에 찍지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBootstrap implements ApplicationRunner {

	private final UserRepository userRepository;

	private final ConfigChangeRecorder changeRecorder;

	@Value("${tc.bootstrap.admin-id:}")
	private String adminId;

	@Value("${tc.bootstrap.admin-password-hash:}")
	private String adminPasswordHash;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.count() > 0) {
			return;
		}
		if (!StringUtils.hasText(adminId) || !StringUtils.hasText(adminPasswordHash)) {
			log.warn("사용자가 한 명도 없고 최초 관리자 설정(TC_BOOTSTRAP_ADMIN_ID, TC_BOOTSTRAP_ADMIN_PASSWORD_HASH)도 없습니다 — "
				+ "로그인할 수 없습니다. 설정 후 재기동하거나 운영 문서의 비상 복구 절차로 사용자를 넣으세요.");
			return;
		}
		if (!adminPasswordHash.startsWith("$2")) {
			log.error("tc.bootstrap.admin-password-hash 는 BCrypt 해시여야 합니다($2a$… 형식). 최초 관리자를 만들지 않습니다.");
			return;
		}
		UserEntity e = new UserEntity();
		e.setId(adminId.trim());
		e.setName("관리자");
		e.setRoles(UserRole.ROLE_ADMIN.name());
		e.setPassword(adminPasswordHash);
		e.setEnabled(true);
		e.setPasswordMustChange(true);
		e.setPasswordChangedAt(new Date());
		userRepository.save(e);
		changeRecorder.created(TargetType.USER, e.getId(), e.getName(), e.info());
		log.info("최초 관리자를 만들었습니다: {} (첫 로그인에서 비밀번호를 바꿔야 합니다)", e.getId());
	}
}
