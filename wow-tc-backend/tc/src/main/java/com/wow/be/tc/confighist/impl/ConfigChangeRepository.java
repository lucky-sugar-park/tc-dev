package com.wow.be.tc.confighist.impl;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;

@Repository
public interface ConfigChangeRepository extends JpaRepository<ConfigChangeEntity, String>, JpaSpecificationExecutor<ConfigChangeEntity> {

	List<ConfigChangeEntity> findByTargetTypeAndTargetIdOrderByChangedAtDesc(TargetType targetType, String targetId);

	@Modifying
	@Query("delete from ConfigChangeEntity c where c.changedAt < :before")
	int deleteOlderThan(@Param("before") Date before);
}
