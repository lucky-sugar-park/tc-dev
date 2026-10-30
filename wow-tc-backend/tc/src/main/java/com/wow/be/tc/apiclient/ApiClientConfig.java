package com.wow.be.tc.apiclient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API 클라이언트 설정을 올린다. 만료 임박 알림({@code ApiKeyExpiryNotifier})이 {@code @Scheduled} 를 쓰므로 스케줄링도 켠다
 * (업무 스케줄 Job 은 Quartz 가 따로 돌린다).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ApiClientProperties.class)
public class ApiClientConfig {
}
