package com.wow.be.tc.delivery.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * 재전송한 결과가 받는 쪽 유니크 키에 걸리면 "이미 있음" 으로 본다 (docs/redelivery-design.md R6).
 */
class DbTableDeliveryChannelTest {

	@Test
	void 중복_키를_알아본다() {
		assertThat(DbTableDeliveryChannel.isDuplicateKey(new DuplicateKeyException("dup"))).isTrue();
		assertThat(DbTableDeliveryChannel.isDuplicateKey(new DataIntegrityViolationException("x",
			new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062)))).isTrue();
		assertThat(DbTableDeliveryChannel.isDuplicateKey(new DataIntegrityViolationException("x",
			new SQLException("duplicate", "23505")))).isTrue();
	}

	@Test
	void 다른_무결성_오류는_중복이_아니다() {
		// NOT NULL 위반(1048)
		assertThat(DbTableDeliveryChannel.isDuplicateKey(new DataIntegrityViolationException("x",
			new SQLIntegrityConstraintViolationException("cannot be null", "23000", 1048)))).isFalse();
		assertThat(DbTableDeliveryChannel.isDuplicateKey(new DataIntegrityViolationException("x"))).isFalse();
	}
}
