package com.wow.be.tc.vplc.management.impl;

import org.springframework.stereotype.Repository;

import com.wow.be.framework.persist.WowSimpleRangeableRepository;

import jakarta.persistence.EntityManager;

/**
 * 연결 이력 구간(Rangeable) 조회 저장소 — 화면의 구간 스크롤 표(docs/range-grid-design.md).
 */
@Repository
public class ConnectionClientHistoryRangeableRepository extends WowSimpleRangeableRepository<ConnectionClientHistoryEntity, Long> {

	public ConnectionClientHistoryRangeableRepository(EntityManager entityManager) {
		super(entityManager, ConnectionClientHistoryEntity.class);
	}
}
