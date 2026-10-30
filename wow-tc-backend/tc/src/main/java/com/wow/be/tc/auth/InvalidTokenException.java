package com.wow.be.tc.auth;

public class InvalidTokenException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public InvalidTokenException() {
		super();
	}
	
	public InvalidTokenException(String message) {
		super(message);
	}
}
