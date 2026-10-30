package com.wow.be.tc.msg.audit.impl;

import org.springframework.stereotype.Repository;

import com.wow.be.framework.persist.WowSimpleRangeableRepository;

import jakarta.persistence.EntityManager;

/**
 * 감사 메시지 양방향 스크롤(Rangeable) 조회 저장소.
 */
@Repository
public class ExchangedMessageAuditRangeableRepository extends WowSimpleRangeableRepository<ExchangedMessageAuditEntity, Long> {

	public ExchangedMessageAuditRangeableRepository(EntityManager entityManager) {
		super(entityManager, ExchangedMessageAuditEntity.class);
	}
}
