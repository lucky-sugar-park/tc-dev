package com.wow.be.tc.schedule.impl;

import java.sql.Timestamp;
import java.util.Date;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.wow.be.tc.schedule.CleanupTarget;

import lombok.extern.slf4j.Slf4j;

/**
 * 오래된 행을 나눠 지운다 — {@code DELETE … LIMIT n} 을 지운 수가 n 보다 적을 때까지 되풀이하고, 문장마다 따로 커밋한다.
 * 한 번에 지우면 몇 달치가 쌓인 표를 오래 잠가 그동안 기록(송수신 감사 등)이 막힌다.
 */
@Component
@Slf4j
public class DataCleaner {

	private final JdbcTemplate jdbc;

	private final int batchSize;

	public DataCleaner(DataSource dataSource, @Value("${tc.schedule.cleanup-batch-size:5000}") int batchSize) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.batchSize = Math.max(batchSize, 1);
	}

	/** @return 지운 행 수 */
	public long deleteOlderThan(CleanupTarget target, Date before) {
		String sql = "DELETE FROM " + target.table() + " WHERE " + target.timeColumn() + " < ? LIMIT " + batchSize;
		Timestamp ts = new Timestamp(before.getTime());
		long total = 0;
		int deleted;
		do {
			deleted = jdbc.update(sql, ts);
			total += deleted;
		} while (deleted >= batchSize);
		log.info("정리: {} {} 이전 {}건", target.table(), before, total);
		return total;
	}
}
