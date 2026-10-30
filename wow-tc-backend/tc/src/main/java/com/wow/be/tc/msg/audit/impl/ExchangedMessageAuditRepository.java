package com.wow.be.tc.msg.audit.impl;

import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangedMessageAuditRepository extends JpaRepository<ExchangedMessageAuditEntity, Long>, JpaSpecificationExecutor<ExchangedMessageAuditEntity> {
	
	@Query("DELETE FROM ExchangedMessageAuditEntity AS a WHERE a.timestamp <= :timestamp")
	@Modifying
	public void deleteByTimestamp(@Param("timestamp") Date timestamp);
	
	@Query("SELECT count(*) FROM ExchangedMessageAuditEntity AS a WHERE a.timestamp <= :timestamp")
	public long countByTimestamp(@Param("timestamp") Date timestamp);

//	public List<ExchangedMessageAuditEntity> findById(Specification<ExchangedMessageAuditEntity> spec, Rangeable rangeable);
}
