package com.wow.be.tc.schedule.impl;

import java.util.Date;
import java.util.List;

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
	
	/** Job 마다 가장 최근 이력 한 행 — 색인 (JOB_NAME, SEQ_NO) */
	@Query("SELECT h FROM ScheduleJobExecHistoryEntity h WHERE h.seqNo IN (SELECT MAX(x.seqNo) FROM ScheduleJobExecHistoryEntity x GROUP BY x.jobName)")
	public List<ScheduleJobExecHistoryEntity> findLatestPerJob();
	
	/** [Job 이름, 실패 수] — 색인 (EXEC_TIME) */
	@Query("SELECT h.jobName, COUNT(h) FROM ScheduleJobExecHistoryEntity h WHERE h.executionResult = 'FAIL' AND h.executionTime >= :since GROUP BY h.jobName")
	public List<Object[]> countFailuresSince(@Param("since") Date since);
}
