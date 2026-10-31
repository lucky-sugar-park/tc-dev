package com.wow.be.tc.interfs.impl;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class InterfaceSpec extends SearchSpec<InterfaceEntity> {

	public Specification<InterfaceEntity> withName(CondType condType, String name) {
		return super.with("name", condType, name);
	}

	public Specification<InterfaceEntity> withPlcName(CondType condType, String plcName) {
		return super.with("plcName", condType, plcName);
	}
	
	public Specification<InterfaceEntity> withPlcNames(CondType condType, List<String> plcNames) {
		return super.with("plcName", condType, plcNames);
	}
	
	public Specification<InterfaceEntity> withInterfaceType(CondType condType, String interfaceType) {
		return super.with("interfaceType", condType, interfaceType);
	}
	
	public Specification<InterfaceEntity> withInterfaceTypes(CondType condType, List<String> interfaceTypes) {
		return super.with("interfaceType", condType, interfaceTypes);
	}
	
	public Specification<InterfaceEntity> withCreatedDate(CondType condType, String createdDate) {
		return super.with("createdDate", condType, createdDate);
	}
}
