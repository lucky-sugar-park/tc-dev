package com.wow.be.tc.interfs.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterfacePointRepository extends JpaRepository<InterfacePointEntity, PropEntityId> {

	@Modifying
	@Query("delete from InterfacePointEntity p where p.pointId.interfaceId = :interfaceId")
	int deleteByInterfaceId(@Param("interfaceId") Long interfaceId);
}
