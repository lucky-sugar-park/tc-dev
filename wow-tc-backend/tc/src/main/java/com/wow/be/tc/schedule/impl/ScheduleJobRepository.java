package com.wow.be.tc.schedule.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleJobRepository extends JpaRepository<ScheduleJobEntity, String>, JpaSpecificationExecutor<ScheduleJobEntity> {

	@Query("DELETE FROM ScheduleJobEntity AS a WHERE a.name = :name")
	@Modifying
	public void deleteByName(@Param("name") String name);
	
	@Query("DELETE FROM ScheduleJobEntity AS a WHERE a.id IN :ids")
	@Modifying
	public void deleteByBatch(@Param("ids") List<String> ids);
}
