package com.wow.be.tc.auth;

import org.springframework.security.core.userdetails.User;

import com.wow.be.framework.security.JwTokenInfo;
import com.wow.be.tc.user.UserInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class SecurityUser extends User {

	private static final long serialVersionUID = 1L;
	
	private JwTokenInfo jwTokenInfo;
	
	private UserInfo userInfo;

	public SecurityUser(UserInfo userInfo) {
		super(
			userInfo.getId(), 
			"{noop}"+userInfo.getPassword(),
			userInfo.getAuthorities()
		);
		this.userInfo=userInfo;
	}
}
