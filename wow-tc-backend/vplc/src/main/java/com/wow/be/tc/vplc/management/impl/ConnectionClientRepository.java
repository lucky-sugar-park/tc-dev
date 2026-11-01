package com.wow.be.tc.vplc.management.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectionClientRepository extends JpaRepository<ConnectionClientEntity, ConnectionClientEntityId>, JpaSpecificationExecutor<ConnectionClientEntity>{

	/** 그 포트에 붙어 있는 연결 수 */
	@org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM ConnectionClientEntity c WHERE c.connectionClientEntityId.vplcId = :vplcId AND c.connectionClientEntityId.port = :port")
	long countByVplcIdAndPort(@org.springframework.data.repository.query.Param("vplcId") String vplcId, @org.springframework.data.repository.query.Param("port") int port);

}
