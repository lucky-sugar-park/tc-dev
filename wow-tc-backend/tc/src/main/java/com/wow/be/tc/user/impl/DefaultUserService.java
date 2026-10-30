package com.wow.be.tc.user.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wow.be.framework.security.JwtRefreshTokenRepository;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.confighist.ConfigChangeInfo.Action;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.user.UserHistoryInfo;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserRole;
import com.wow.be.tc.user.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자 관리 (docs/auth-and-audit-design.md §3).
 *
 * <ul>
 *   <li>관리자가 다른 사용자를 바꾼 일은 설정 변경 이력(대상 USER)에, 사용자 자신의 행위(로그인·로그아웃·비밀번호 변경)는
 *       사용 이력(USER_HIST)에 남긴다.</li>
 *   <li>비밀번호 규칙: 10자 이상, 아이디와 같지 않을 것, 지금 비밀번호와 같지 않을 것.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUserService implements UserService, UserDetailsService {

	/** 영문·숫자와 . _ - (로그인 아이디) */
	private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{2,50}");

	private static final int MIN_PASSWORD_LENGTH = 10;

	/** 비밀번호 변경 강제 여부 캐시 시간 — 요청마다 DB 를 읽지 않는다. 이 인스턴스의 변경은 바로 지운다 */
	private static final long MUST_CHANGE_CACHE_MS = 10_000;

	private final UserRepository userRepository;

	private final UserHistoryRepository userHistoryRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtRefreshTokenRepository tokenRepository;

	private final ConfigChangeRecorder changeRecorder;

	private final Map<String, long[]> mustChangeCache = new ConcurrentHashMap<>();

	// ── 인증(framework CustomAuthenticationProvider) ──

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository.findById(username == null ? "" : username)
			.map(UserEntity::info)
			.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다"));
	}

	// ── 이력 ──

	@Override
	@Transactional
	public void recordUserHistory(String userId, String oper, String ipAddress, String remark) {
		try {
			UserHistoryInfo h = new UserHistoryInfo();
			h.setUserId(userId);
			h.setOper(oper);
			h.setTimestamp(new Date());
			h.setIpAddress(ipAddress);
			h.setRemark(remark);
			UserHistoryEntity e = new UserHistoryEntity();
			e.from(h);
			userHistoryRepository.save(e);
		} catch (RuntimeException e) {
			log.warn("사용 이력을 남기지 못했습니다: user={}, oper={}", userId, oper, e);
		}
	}

	// ── 조회 ──

	@Override
	public Optional<UserInfo> findUser(String id) {
		return id == null ? Optional.empty() : userRepository.findById(id).map(UserEntity::info);
	}

	@Override
	public UserInfo findUserById(String id) throws ToolControlException {
		return findUser(id).orElseThrow(() -> ToolControlException.of(ToolControlException.NOT_FOUND, "사용자를 찾을 수 없습니다: " + id));
	}

	@Override
	public List<UserInfo> searchUsers() {
		return userRepository.findAllByOrderByIdAsc().stream().map(UserEntity::info).toList();
	}

	// ── 관리자의 변경 ──

	@Override
	@Transactional
	public UserInfo createUser(UserInfo info, String initialPassword) throws ToolControlException {
		if (info.getId() == null || !ID_PATTERN.matcher(info.getId()).matches()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"아이디는 영문·숫자와 . _ - 로 2~50자입니다: " + info.getId());
		}
		if (userRepository.existsById(info.getId())) {
			throw ToolControlException.of(ToolControlException.CONFLICT, "같은 아이디의 사용자가 이미 있습니다: " + info.getId());
		}
		validateProfile(info);
		validatePassword(info.getId(), initialPassword, null);

		UserEntity e = new UserEntity();
		e.setId(info.getId());
		e.from(info);
		e.setPassword(passwordEncoder.encode(initialPassword));
		e.setEnabled(true);
		e.setPasswordMustChange(true);
		e.setPasswordChangedAt(new Date());
		UserInfo created = userRepository.save(e).info();
		changeRecorder.created(TargetType.USER, created.getId(), created.getName(), created);
		log.info("사용자 생성: {} {}", created.getId(), created.getUserRoles());
		return created;
	}

	@Override
	@Transactional
	public UserInfo updateUser(UserInfo info) throws ToolControlException {
		UserEntity e = load(info.getId());
		validateProfile(info);
		UserInfo before = e.info();
		if (before.hasRole(UserRole.ROLE_ADMIN) && !info.hasRole(UserRole.ROLE_ADMIN)) {
			guardLastAdmin(before, "관리자 역할을 뺄");
		}
		e.from(info);
		UserInfo after = userRepository.save(e).info();
		changeRecorder.updated(TargetType.USER, after.getId(), after.getName(), before, after);
		return after;
	}

	@Override
	@Transactional
	public UserInfo setEnabled(String id, boolean enabled) throws ToolControlException {
		UserEntity e = load(id);
		UserInfo before = e.info();
		if (!enabled) {
			guardSelf(id, "사용 중지할");
			if (before.hasRole(UserRole.ROLE_ADMIN)) {
				guardLastAdmin(before, "사용 중지할");
			}
		}
		e.setEnabled(enabled);
		UserInfo after = userRepository.save(e).info();
		if (!enabled) {
			revokeTokens(id);
		}
		changeRecorder.record(TargetType.USER, id, after.getName(), enabled ? Action.ENABLE : Action.DISABLE, null, null, null);
		return after;
	}

	@Override
	@Transactional
	public void resetPassword(String id, String newPassword) throws ToolControlException {
		UserEntity e = load(id);
		validatePassword(id, newPassword, null);
		e.setPassword(passwordEncoder.encode(newPassword));
		e.setPasswordMustChange(true);
		e.setPasswordChangedAt(new Date());
		userRepository.save(e);
		mustChangeCache.remove(id);
		revokeTokens(id);
		changeRecorder.controlled(TargetType.USER, id, e.getName(), "password-reset");
	}

	@Override
	@Transactional
	public void deleteUser(String id) throws ToolControlException {
		UserEntity e = load(id);
		UserInfo before = e.info();
		guardSelf(id, "삭제할");
		if (before.hasRole(UserRole.ROLE_ADMIN)) {
			guardLastAdmin(before, "삭제할");
		}
		userRepository.delete(e);
		revokeTokens(id);
		mustChangeCache.remove(id);
		changeRecorder.deleted(TargetType.USER, id, before.getName(), before);
	}

	// ── 본인 ──

	@Override
	@Transactional
	public void changeOwnPassword(String id, String currentPassword, String newPassword, String ipAddress)
			throws ToolControlException {
		UserEntity e = load(id);
		if (currentPassword == null || !passwordEncoder.matches(currentPassword, e.getPassword())) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "현재 비밀번호가 맞지 않습니다.");
		}
		validatePassword(id, newPassword, e.getPassword());
		e.setPassword(passwordEncoder.encode(newPassword));
		e.setPasswordMustChange(false);
		e.setPasswordChangedAt(new Date());
		userRepository.save(e);
		mustChangeCache.remove(id);
		recordUserHistory(id, "PASSWORD_CHANGE", ipAddress, null);
	}

	@Override
	@Transactional
	public void recordLoginSuccess(String id, String ipAddress) {
		userRepository.findById(id).ifPresent(e -> {
			e.setLastLoginAt(new Date());
			e.setLastLoginIp(ipAddress);
			userRepository.save(e);
		});
		recordUserHistory(id, "LOGIN", ipAddress, null);
	}

	@Override
	public boolean mustChangePassword(String id) {
		long now = System.currentTimeMillis();
		long[] cached = mustChangeCache.get(id);
		if (cached != null && now - cached[1] < MUST_CHANGE_CACHE_MS) {
			return cached[0] == 1L;
		}
		boolean must = userRepository.findById(id).map(e -> Boolean.TRUE.equals(e.getPasswordMustChange())).orElse(false);
		mustChangeCache.put(id, new long[] { must ? 1L : 0L, now });
		return must;
	}

	// ── 공통 ──

	private UserEntity load(String id) throws ToolControlException {
		return userRepository.findById(id == null ? "" : id)
			.orElseThrow(() -> ToolControlException.of(ToolControlException.NOT_FOUND, "사용자를 찾을 수 없습니다: " + id));
	}

	private void validateProfile(UserInfo info) throws ToolControlException {
		if (!StringUtils.hasText(info.getName())) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "사용자 이름이 없습니다.");
		}
		if (info.getUserRoles() == null || info.getUserRoles().isEmpty() || info.getUserRoles().contains(UserRole.ROLE_UNKNOWN)) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"역할을 하나 이상 고릅니다(ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER): " + info.getUserRoles());
		}
	}

	private void validatePassword(String id, String password, String currentHash) throws ToolControlException {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상입니다.");
		}
		if (password.equalsIgnoreCase(id)) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "비밀번호를 아이디와 같게 할 수 없습니다.");
		}
		if (currentHash != null && passwordEncoder.matches(password, currentHash)) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "지금 비밀번호와 다른 비밀번호를 씁니다.");
		}
	}

	/** 활성 관리자가 이 사용자 하나뿐이면 거부한다 */
	private void guardLastAdmin(UserInfo target, String what) throws ToolControlException {
		long otherActiveAdmins = userRepository.findAll().stream()
			.map(UserEntity::info)
			.filter(u -> !u.getId().equals(target.getId()) && u.isEnabled() && u.hasRole(UserRole.ROLE_ADMIN))
			.count();
		if (otherActiveAdmins == 0) {
			throw ToolControlException.of(ToolControlException.CONFLICT,
				"마지막 활성 관리자는 " + what + " 수 없습니다: " + target.getId());
		}
	}

	private void guardSelf(String id, String what) throws ToolControlException {
		String current = SecurityContextHolder.getContext().getAuthentication() != null
			? SecurityContextHolder.getContext().getAuthentication().getName() : null;
		if (id.equals(current)) {
			throw ToolControlException.of(ToolControlException.CONFLICT, "자기 자신은 " + what + " 수 없습니다.");
		}
	}

	/** 그 사용자의 토큰(access·refresh 키 모두)을 지운다 — framework JwtFilter 는 저장된 토큰만 통과시키므로 바로 끊긴다 */
	private void revokeTokens(String id) {
		try {
			tokenRepository.deleteAll(tokenRepository.findAllByUserId(id));
		} catch (RuntimeException e) {
			log.warn("사용자 토큰을 지우지 못했습니다: {}", id, e);
		}
	}
}
