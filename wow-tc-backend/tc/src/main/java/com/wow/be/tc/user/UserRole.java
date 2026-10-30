package com.wow.be.tc.user;

/**
 * 사용자 역할 (docs/auth-and-audit-design.md §4.1). 권한 표는 {@code TcPermissionChecker} 에 있다.
 *
 * <ul>
 *   <li>ADMIN — 전부(사용자·API 클라이언트 관리 포함)</li>
 *   <li>OPERATOR — PLC·인터페이스·WebHook·전달 정의·스케줄의 등록·수정·삭제·제어, PLC 요청</li>
 *   <li>VIEWER — 조회만</li>
 *   <li>CLIENT — API 키 주체 전용(PLC 요청). 사용자 역할로는 기존 JWT 호출자 전환 기간에만 쓴다(설계 §6.10)</li>
 * </ul>
 * MANAGER·EMPLOYEE 는 레거시 역할이다 — 각각 OPERATOR·VIEWER 로 취급한다(사용자 정리 때 바꾼다).
 */
public enum UserRole {

	ROLE_ADMIN("ROLE_ADMIN"),
	ROLE_OPERATOR("ROLE_OPERATOR"),
	ROLE_VIEWER("ROLE_VIEWER"),
	ROLE_MANAGER("ROLE_MANAGER"),
	ROLE_EMPLOYEE("ROLE_EMPLOYEE"),
	ROLE_CLIENT("ROLE_CLIENT"),
	ROLE_UNKNOWN("ROLE_UNKNOWN");

	private String name;

	private UserRole(String name) {
		this.name=name;
	}

	public String getName() {
		return name;
	}

	public static UserRole toUserRole(String name) {
		for (UserRole r : values()) {
			if (r.name.equals(name)) {
				return r;
			}
		}
		return ROLE_UNKNOWN;
	}
}
