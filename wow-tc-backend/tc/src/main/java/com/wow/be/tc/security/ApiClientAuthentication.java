package com.wow.be.tc.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.wow.be.tc.apiclient.VerifiedApiClient;

/**
 * API 키로 인증된 타 시스템. principal 이름은 clientId, 권한은 {@code ROLE_CLIENT} 하나다.
 */
public class ApiClientAuthentication extends AbstractAuthenticationToken {

	private static final long serialVersionUID = 1L;

	public static final String ROLE_CLIENT = "ROLE_CLIENT";

	private final transient VerifiedApiClient client;

	public ApiClientAuthentication(VerifiedApiClient client) {
		super(List.of(new SimpleGrantedAuthority(ROLE_CLIENT)));
		this.client = client;
		setAuthenticated(true);
	}

	public VerifiedApiClient getClient() {
		return client;
	}

	@Override
	public Object getCredentials() {
		return "";
	}

	@Override
	public Object getPrincipal() {
		return client.clientId();
	}

	@Override
	public String getName() {
		return client.clientId();
	}
}
