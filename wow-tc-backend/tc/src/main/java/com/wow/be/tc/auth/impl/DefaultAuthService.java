package com.wow.be.tc.auth.impl;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.wow.be.framework.security.JwTokenInfo;
import com.wow.be.framework.security.JwTokenProvider;
import com.wow.be.framework.security.JwtRefreshTokenEntity;
import com.wow.be.framework.security.JwtRefreshTokenRepository;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.auth.AuthService;
import com.wow.be.tc.auth.ExpiredRefreshTokenException;
import com.wow.be.tc.auth.InvalidTokenException;
import com.wow.be.tc.auth.LoginRequest;
import com.wow.be.tc.auth.NoMatchedRemoteAddressException;
import com.wow.be.tc.auth.SecurityUser;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserService;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인·재발급·로그아웃.
 *
 * <p>framework {@code JwtFilter} 는 요청의 access token 을 Redis({@link JwtRefreshTokenRepository})에서 찾아 검증한다.
 * 그래서 토큰 정보를 access token 키와 refresh token 키로 두 번 저장하고, 재발급·로그아웃 때 두 키를 함께 지운다.
 */
@Service
@Slf4j
public class DefaultAuthService implements AuthService {

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwTokenProvider jwTokenProvider;

	@Autowired
	private UserService userService;

	@Autowired
	private JwtRefreshTokenRepository jwRefreshTokenRepository;

	@Override
	@Transactional
	public SecurityUser login(LoginRequest loginRequest) {
		log.info("");

		Authentication authentication = this.authenticate(loginRequest.getId(), loginRequest.getPassword());
		JwTokenInfo tokenInfo=this.jwTokenProvider.createTokens(authentication);
		this.saveTokens(tokenInfo, loginRequest.getId(), loginRequest.getRemoteAddress());

		UserInfo uInfo = (UserInfo) authentication.getDetails();

		SecurityUser suser = new SecurityUser(uInfo);
		suser.setJwTokenInfo(tokenInfo);

		uInfo.setLogined(true);
		uInfo.setLoginedIp(loginRequest.getRemoteAddress());
		try {
			userService.updateUser(uInfo, "LOGIN");
		} catch (ToolControlException e) {
			log.warn("로그인 사용자 정보 갱신 실패: {}", uInfo.getId(), e);
		}

		return suser;
	}

	@Override
	@Transactional
	public SecurityUser reissue(String remoteAddress, String refreshToken) {
		log.info("");
		try {
			boolean valid=jwTokenProvider.validateToken(refreshToken, remoteAddress, true);
			if(!valid) {
				throw new InvalidTokenException("갱신 토큰이 올바르지 않습니다.");
			}
		} catch (ExpiredJwtException e) {
			log.info("", e);
			throw e;
		} catch (NoMatchedRemoteAddressException e) {
			log.info("");
			throw e;
		}

		// refresh token rotation 기법적용
		Optional<JwtRefreshTokenEntity> optional = jwRefreshTokenRepository.findById(refreshToken);
		if (optional.isEmpty() || !refreshToken.equals(optional.get().getRefreshToken())) {
			throw new InvalidTokenException("저장된 갱신 토큰과 일치하지 않습니다.");
		}

		JwtRefreshTokenEntity refreshTokenEntity = optional.get();

		if(refreshTokenEntity.isExpired(System.currentTimeMillis())) {
			throw new ExpiredRefreshTokenException("갱신 토큰이 만료되었습니다. 다시 로그인하세요.");
		}

		// create new token (access / refresh)
		UserInfo uInfo = userService.findUserById(refreshTokenEntity.getUserId());
		UsernamePasswordAuthenticationToken authenticationToken =
				new UsernamePasswordAuthenticationToken(uInfo.getId(), uInfo.getPassword(), uInfo.getAuthorities());
		JwTokenInfo tokenInfo=this.jwTokenProvider.createTokens(authenticationToken);

		// 기존 토큰 정보 삭제 (refresh token 키, access token 키)
		this.deleteTokens(refreshTokenEntity);

		// 새 토큰 정보 저장
		this.saveTokens(tokenInfo, uInfo.getId(), remoteAddress);

		SecurityUser suser = new SecurityUser(uInfo);
		suser.setJwTokenInfo(tokenInfo);

		return suser;
	}

	@Override
	public void logout(String userId, String remoteAddress, String refreshToken) {
		log.info("");
		// check validation
		try {
			boolean valid=jwTokenProvider.validateToken(refreshToken, remoteAddress, true);
			if(!valid) {
				throw new InvalidTokenException("갱신 토큰이 올바르지 않습니다.");
			}
		} catch (ExpiredRefreshTokenException t) {
			throw new InvalidTokenException("갱신 토큰이 올바르지 않습니다.");
		}

		Optional<JwtRefreshTokenEntity> optional = jwRefreshTokenRepository.findById(refreshToken);
		// delete refresh token information
		optional.ifPresent(this::deleteTokens);
		if (optional.isEmpty() || !refreshToken.equals(optional.get().getRefreshToken())) {
			throw new InvalidTokenException("저장된 갱신 토큰과 일치하지 않습니다.");
		}

		// update user information
		try {
			UserInfo uInfo = userService.findUserById(userId);
			uInfo.setLogined(false);
			uInfo.setLoginedIp("");
			userService.updateUser(uInfo, "LOGOUT");
		} catch (ToolControlException e) {
			log.warn("로그아웃 사용자 정보 갱신 실패: {}", userId, e);
		}
	}

	/** access token 키와 refresh token 키로 같은 토큰 정보를 저장한다(framework JwtFilter 는 access token 키로 찾는다). */
	private void saveTokens(JwTokenInfo tokenInfo, String userId, String remoteAddress) {
		JwtRefreshTokenEntity tokenEntity = new JwtRefreshTokenEntity();
		tokenEntity.setId(tokenInfo.getAccessToken());
		tokenEntity.setAccessToken(tokenInfo.getAccessToken());
		tokenEntity.setRefreshToken(tokenInfo.getRefreshToken());
		tokenEntity.setUserId(userId);
		tokenEntity.setRemoteAddress(remoteAddress);
		tokenEntity.expireAt(tokenInfo.getRefreshTokenExpiredTime(), System.currentTimeMillis());
		jwRefreshTokenRepository.save(tokenEntity);

		tokenEntity.setId(tokenInfo.getRefreshToken());
		jwRefreshTokenRepository.save(tokenEntity);
	}

	/** 토큰 정보의 두 키(refresh token, access token)를 지운다. */
	private void deleteTokens(JwtRefreshTokenEntity tokenEntity) {
		if(tokenEntity.getRefreshToken()!=null) {
			jwRefreshTokenRepository.deleteById(tokenEntity.getRefreshToken());
		}
		if(tokenEntity.getAccessToken()!=null) {
			jwRefreshTokenRepository.deleteById(tokenEntity.getAccessToken());
		}
	}

	private Authentication authenticate(String userId, String password) {
		UsernamePasswordAuthenticationToken authenticationToken =
				new UsernamePasswordAuthenticationToken(userId, password);
		Authentication authentication = authenticationManager.authenticate(authenticationToken);
		log.info("authentication: {}", authentication);
		return authentication;
	}
}
