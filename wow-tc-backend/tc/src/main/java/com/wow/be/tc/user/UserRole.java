package com.wow.be.tc.user;

public enum UserRole {

	ROLE_ADMIN("ROLE_ADMIN"), 
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
		switch(name) {
		case "ROLE_ADMIN":
			return ROLE_ADMIN;
		case "ROLE_MANAGER":
			return ROLE_MANAGER;
		case "ROLE_EMPLOYEE":
			return ROLE_EMPLOYEE;
		case "ROLE_CLIENT":
			return ROLE_CLIENT;
		default:
			return ROLE_UNKNOWN;
		}
	}
}
