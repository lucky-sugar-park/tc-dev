package com.wow.be.tc.auth;

public class ExpiredRefreshTokenException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public ExpiredRefreshTokenException() {
		super();
	}
	
	public ExpiredRefreshTokenException(String message) {
		super(message);
	}
}
