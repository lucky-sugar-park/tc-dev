package com.wow.be.tc.apiclient.impl;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.wow.be.tc.apiclient.ApiClientKeyInfo.KeyStatus;

@Repository
public interface ApiClientKeyRepository extends JpaRepository<ApiClientKeyEntity, String> {

	List<ApiClientKeyEntity> findByClientIdOrderByCreatedDateDesc(String clientId);

	List<ApiClientKeyEntity> findByStatusAndExpiresAtBetween(KeyStatus status, Date from, Date to);

	void deleteByClientId(String clientId);

	/** 마지막 사용 기록 — 엔티티를 읽지 않고 바로 갱신한다(요청 경로라 가볍게). */
	@Modifying
	@Query("update ApiClientKeyEntity k set k.lastUsedAt = :at, k.lastUsedIp = :ip where k.keyId = :keyId")
	int touch(@Param("keyId") String keyId, @Param("at") Date at, @Param("ip") String ip);
}
