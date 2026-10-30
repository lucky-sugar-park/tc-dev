package com.wow.be.tc.webhook.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebHookRepository extends JpaRepository<WebHookEntity, Long>, JpaSpecificationExecutor<WebHookEntity>{

	@Query("DELETE FROM WebHookEntity AS a WHERE a.name = :name")
	@Modifying
	public void deleteByName(@Param("name") String name);
}
