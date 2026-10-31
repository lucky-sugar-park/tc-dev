package com.wow.be.tc.ui.code;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommonCodeRepository extends JpaRepository<CommonCodeEntity, String> {

	List<CommonCodeEntity> findByParentOrderByCode(String parent);

	List<CommonCodeEntity> findByParentIsNullOrderByCode();

	boolean existsByParent(String parent);
}
