package com.wow.be.tc.auth;

public class NoMatchedRemoteAddressException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public NoMatchedRemoteAddressException() {
		super();
	}
	
	public NoMatchedRemoteAddressException(String message) {
		super(message);
	}
}
