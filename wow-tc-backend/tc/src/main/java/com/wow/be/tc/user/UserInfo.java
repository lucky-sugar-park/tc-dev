package com.wow.be.tc.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Data
@EqualsAndHashCode(callSuper=false)
public class UserInfo extends AbstractInfo implements UserDetails {

	private static final long serialVersionUID = 1L;

	private String id;

	@JsonIgnore
	private String password;
	
	private String name;
	
	private String email;
	
	private List<UserRole> userRoles;
	
	private String phone;
	
	private String extraInfo;

	@JsonIgnore
	private Boolean logined;
	
	@JsonIgnore
	private String loginedIp;

	private Boolean enabled;
	
	public UserInfo() {
		userRoles=new ArrayList<>();
	}
	
	public void addRole(String role) {
		addRole(UserRole.toUserRole(role));
	}
	
	public void addRole(UserRole role) {
		userRoles.add(role);
	}

	@Override
	public String toPlainText() {
		StringBuilder sb=new StringBuilder();
		return sb.toString();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		Collection<? extends GrantedAuthority> authorities  = 
        		this.userRoles.
        		stream().
        		map(role->new SimpleGrantedAuthority(role.name())).
        		collect(Collectors.toList());
		return authorities;
	}

	@Override
	public String getUsername() {
		return this.id;
	}
	
	@Override
	public String getPassword() {
		return this.password;
	}
}
