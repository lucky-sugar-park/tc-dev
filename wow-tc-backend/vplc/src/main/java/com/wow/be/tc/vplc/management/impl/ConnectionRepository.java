package com.wow.be.tc.vplc.management.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectionRepository extends JpaRepository<ConnectionEntity, ConnectionEntityId>, JpaSpecificationExecutor<ConnectionEntity>{

}
