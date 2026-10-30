package com.wow.be.tc.confighist;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 보존 기간({@code tc.config-history.retention-days}, 기본 365일)이 지난 설정 변경 이력을 매일 지운다(설계 §5.4).
 * 지운 사실도 SYSTEM 행위자로 한 줄 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigChangePurger {

	private final ConfigChangeService service;

	private final ConfigChangeRecorder recorder;

	@Value("${tc.config-history.retention-days:365}")
	private int retentionDays;

	@Scheduled(cron = "${tc.config-history.purge-cron:0 30 3 * * *}")
	@Transactional
	public void purge() {
		Date before = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays));
		long count = service.purgeOlderThan(before);
		if (count > 0) {
			log.info("보존 기간({}일)이 지난 설정 변경 이력 {}건을 지웠습니다", retentionDays, count);
			recorder.controlled(TargetType.OPTION, "config-change-hist", "설정 변경 이력", "purge:" + count);
		}
	}
}
