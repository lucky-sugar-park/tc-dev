package com.wow.be.tc.vplc.management.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface VirtualPlcRepository extends JpaRepository<VirtualPlcEntity, String>, JpaSpecificationExecutor<VirtualPlcEntity> {
	
	@Query("DELETE FROM VirtualPlcEntity AS a WHERE a.name = :vplcName")
	@Modifying
	@Transactional
	public void deleteVirtualPlcByName(@Param("vplcName") String vplcName);
	
	@Query("SELECT conn FROM ConnectionEntity AS conn WHERE conn.connectionEntityId.vplcId = :vplcId")
	public List<ConnectionEntity> searchConnectionsByPlcId(@Param("vplcId") String vplcId);
	
	// 이 함수로 업데이트 한 후에, 동일한 트랜잭션에서 find를 하면 이전의 상태 값이 검색됨 - 트랜잭션 관리범위를 살펴봐야 함
	@Query("UPDATE VirtualPlcEntity AS a SET a.status = :status, a.published= :isPublished, a.updatedBy='SYSTEM', a.updatedDate=current_timestamp WHERE (a.id = :vplcId OR a.name = :vplcName)")
	@Modifying(clearAutomatically=true)
	@Transactional
	public void updateVirtualPlcStatus(@Param("vplcId") String vplcId, @Param("vplcName") String vplcName, @Param("status") String status, @Param("isPublished") boolean isPublished);	
}
