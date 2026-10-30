package com.wow.be.tc.vplc.management.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VirtualPlcHistoryRepository  extends JpaRepository<VirtualPlcHistoryEntity, Long>, JpaSpecificationExecutor<VirtualPlcHistoryEntity> {

}
