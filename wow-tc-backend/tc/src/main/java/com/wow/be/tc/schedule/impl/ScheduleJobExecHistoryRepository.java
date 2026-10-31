package com.wow.be.tc.schedule.impl;

import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleJobExecHistoryRepository extends JpaRepository<ScheduleJobExecHistoryEntity, Long>, JpaSpecificationExecutor<ScheduleJobExecHistoryEntity> {

	@Query("DELETE FROM ScheduleJobExecHistoryEntity AS a WHERE a.jobName = :jobName")
	@Modifying
	public void deleteByScheduleJobName(@Param("jobName") String jobName);
	
	@Query("DELETE FROM ScheduleJobExecHistoryEntity AS a WHERE a.executionTime BETWEEN :from AND :to")
	@Modifying
	public void deleteScheduleJobExecHistoryBetweenExecTime(@Param("from") Date from, @Param("to") Date to);
}
