package com.wow.be.tc.schedule.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleJobDetailRepository extends JpaRepository<ScheduleJobDetailEntity, ScheduleJobDetailEntityId>, JpaSpecificationExecutor<ScheduleJobDetailEntity> {

	@Query("DELETE FROM ScheduleJobDetailEntity AS a WHERE a.entityId.scheduleJobId = :jobId")
	@Modifying
	public void deleteByScheduleJobId(@Param("jobId") String jobId);
	
	@Query("SELECT a FROM ScheduleJobDetailEntity AS a WHERE a.entityId.scheduleJobId = :scheduleJobId")
	public List<ScheduleJobDetailEntity> findAllByScheduleJobId(@Param("scheduleJobId") String scheduleJobId);
}
