package com.wow.be.tc.plc.management.impl;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PlcConnectionEventHistRepository extends JpaRepository<PlcConnectionEventHistEntity, Long>, JpaSpecificationExecutor<PlcConnectionEventHistEntity>{

}
