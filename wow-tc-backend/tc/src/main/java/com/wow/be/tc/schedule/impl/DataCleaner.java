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
 * 오래된 행을 나눠 지운다 (docs/data-retention.md §4).
 *
 * <ol>
 *   <li>기준 시각 이전 행의 가장 큰 기본 키를 찾는다 — 잠그지 않는 읽기다.</li>
 *   <li>{@code DELETE … WHERE 키 <= ? AND 시각 < ? LIMIT n} 을 지운 수가 n 보다 적을 때까지 되풀이하고, 문장마다 따로 커밋한다.</li>
 * </ol>
 * 시각 조건만으로 지우면 시각 색인이 없을 때 마지막 한 번이 표 전체를 훑으며 훑은 행을 모두 잠가(REPEATABLE READ) 그동안
 * 새 기록(송수신 감사 등)이 막힌다. 기본 키 범위로 지우면 잠그는 행이 지울 범위 안으로 줄고, 새 행은 범위 밖(더 큰 키)에 들어간다.
 * 시각 조건도 함께 두는 것은 키 순서와 시각 순서가 다를 수 있어서다(송수신 감사의 시각은 요청 본문의 timestamp).
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
		Timestamp ts = new Timestamp(before.getTime());
		Long maxId = jdbc.queryForObject("SELECT MAX(" + target.idColumn() + ") FROM " + target.table()
			+ " WHERE " + target.timeColumn() + " < ?", Long.class, ts);
		long total = 0;
		if (maxId != null) {
			String sql = "DELETE FROM " + target.table() + " WHERE " + target.idColumn() + " <= ? AND "
				+ target.timeColumn() + " < ? LIMIT " + batchSize;
			int deleted;
			do {
				deleted = jdbc.update(sql, maxId, ts);
				total += deleted;
			} while (deleted >= batchSize);
		}
		log.info("정리: {} {} 이전 {}건", target.table(), before, total);
		return total;
	}
}
