package com.wow.be.tc.apiclient;

import java.util.Date;

/**
 * 새로 발급한 API 키. {@code apiKey} 는 이 응답에서 한 번만 보이고 서버에는 해시만 남는다.
 */
public record IssuedApiKey(String clientId, String keyId, String apiKey, Date expiresAt) {
}
