package com.wow.be.tc.schedule.impl;

import org.springframework.stereotype.Repository;

import com.wow.be.framework.persist.WowSimpleRangeableRepository;

import jakarta.persistence.EntityManager;

/**
 * 스케줄 Job 실행 이력 구간(Rangeable) 조회 저장소 — 화면의 구간 스크롤 표(docs/range-grid-design.md).
 */
@Repository
public class ScheduleJobExecHistoryRangeableRepository extends WowSimpleRangeableRepository<ScheduleJobExecHistoryEntity, Long> {

	public ScheduleJobExecHistoryRangeableRepository(EntityManager entityManager) {
		super(entityManager, ScheduleJobExecHistoryEntity.class);
	}
}
