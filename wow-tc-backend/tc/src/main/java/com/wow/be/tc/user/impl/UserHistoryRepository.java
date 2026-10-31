package com.wow.be.tc.user.impl;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserHistoryRepository extends JpaRepository<UserHistoryEntity, String>, JpaSpecificationExecutor<UserHistoryEntity>{

	List<UserHistoryEntity> findByUserIdOrderBySeqNoDesc(String userId, Pageable pageable);

}
