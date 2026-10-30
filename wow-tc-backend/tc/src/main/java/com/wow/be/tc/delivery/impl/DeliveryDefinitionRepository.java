package com.wow.be.tc.delivery.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;

@Repository
public interface DeliveryDefinitionRepository extends JpaRepository<DeliveryDefinitionEntity, String> {

	List<DeliveryDefinitionEntity> findByInterfaceNameOrderByIdAsc(String interfaceName);

	List<DeliveryDefinitionEntity> findByInterfaceNameAndUseTrueOrderByIdAsc(String interfaceName);

	boolean existsByInterfaceNameAndDeliveryTypeAndTargetName(String interfaceName, DeliveryType deliveryType, String targetName);
}
