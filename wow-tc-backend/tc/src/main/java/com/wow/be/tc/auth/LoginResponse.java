package com.wow.be.tc.auth;

import com.wow.be.tc.user.UserInfo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 로그인·토큰 재발급 응답. 액세스 토큰은 {@code Authorization} 헤더에도 싣는다(기존 호출자) — 본문의 {@code accessToken} 은 화면
 * (MES shared 셸)이 읽는다(docs/frontend-design.md F4).
 */
@Data
@AllArgsConstructor
public class LoginResponse {

	private Boolean result;

	private String accessToken;

	private UserInfo userInfo;
}
