package com.wow.be.tc.vplc.opcua;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OpcUaTagRepository extends JpaRepository<OpcUaTagEntity, String> {

	List<OpcUaTagEntity> findByVplcIdOrderByPath(String vplcId);

	@Query("DELETE FROM OpcUaTagEntity AS t WHERE t.vplcId = :vplcId")
	@Modifying
	@Transactional
	void deleteByVplcId(@Param("vplcId") String vplcId);
}
