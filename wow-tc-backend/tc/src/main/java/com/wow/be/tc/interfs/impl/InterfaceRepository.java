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

	// 삭제는 JPQL 일괄 delete 를 쓰지 않는다 — cascade(점)를 타지 않아 외래 키 오류가 난다. deleteById·delete(엔티티)로.

	@Query("UPDATE InterfaceEntity AS a SET a.use = :use WHERE a.id IN :ids")
	@Modifying
	public void updateApplyUseByBatch(@Param("ids") List<Long> ids, @Param("use") boolean use);
}
