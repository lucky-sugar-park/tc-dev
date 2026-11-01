package com.wow.be.tc.confighist.impl;

import org.springframework.stereotype.Repository;

import com.wow.be.framework.persist.WowSimpleRangeableRepository;

import jakarta.persistence.EntityManager;

/**
 * 설정 변경 이력 구간(Rangeable) 조회 저장소 — 화면의 구간 스크롤 표(docs/range-grid-design.md).
 */
@Repository
public class ConfigChangeRangeableRepository extends WowSimpleRangeableRepository<ConfigChangeEntity, String> {

	public ConfigChangeRangeableRepository(EntityManager entityManager) {
		super(entityManager, ConfigChangeEntity.class);
	}
}
