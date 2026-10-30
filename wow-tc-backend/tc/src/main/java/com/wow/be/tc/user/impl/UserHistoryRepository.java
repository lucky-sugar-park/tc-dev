package com.wow.be.tc.user.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserHistoryRepository extends JpaRepository<UserHistoryEntity, String>, JpaSpecificationExecutor<UserHistoryEntity>{

}
