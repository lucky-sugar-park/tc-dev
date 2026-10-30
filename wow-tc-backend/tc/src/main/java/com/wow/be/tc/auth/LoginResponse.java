package com.wow.be.tc.auth;

import com.wow.be.tc.user.UserInfo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

	private Boolean result;
	
	private UserInfo userInfo;
}
