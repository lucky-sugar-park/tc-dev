package com.wow.be.tc.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class LoginRequest {

	private String id;
	
	private String password;
	
	@JsonIgnore
	private String remoteAddress;
}
