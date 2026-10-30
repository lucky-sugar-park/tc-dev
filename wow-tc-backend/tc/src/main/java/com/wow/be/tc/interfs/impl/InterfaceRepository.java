package com.wow.be.tc.interfs.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterfaceRepository extends JpaRepository<InterfaceEntity, Long>, JpaSpecificationExecutor<InterfaceEntity> {

	@Query("DELETE FROM InterfaceEntity AS a WHERE a.name = :name")
	@Modifying
	public void deleteByName(@Param("name") String name);
	
	@Query("DELETE FROM InterfaceEntity AS a WHERE a.id IN :ids")
	@Modifying
	public void deleteByBatch(@Param("ids") List<Long> ids);
	
	@Query("UPDATE InterfaceEntity AS a SET a.use = :use WHERE a.id IN :ids")
	@Modifying
	public void updateApplyUseByBatch(@Param("ids") List<Long> ids, boolean use);
}
