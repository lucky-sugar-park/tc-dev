package com.wow.be.tc.vplc.management.impl;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class VirtualPlcSpec extends SearchSpec<VirtualPlcEntity> {

	public Specification<VirtualPlcEntity> withId(CondType condType, String id) {
		return super.with("id", condType, id);
	}

	public Specification<VirtualPlcEntity> withName(CondType condType, String name) {
		return super.with("name", condType, name);
	}

	public Specification<VirtualPlcEntity> withStatus(CondType condType, String status) {
		return super.with("status", condType, status);
	}
	
	public Specification<VirtualPlcEntity> withFrameFormat(CondType condType, String frameFormat) {
		return super.with("frameFormat", condType, frameFormat);
	}
	
	public Specification<VirtualPlcEntity> withIpAddress(CondType condType, String ipAddress) {
		return super.with("ipAddress", condType, ipAddress);
	}
	
	public Specification<VirtualPlcEntity> withStartPort(CondType condType, String startPort) {
		return super.with("startPort", condType, startPort);
	}
	
	public Specification<VirtualPlcEntity> withPortCount(CondType condType, String portCount) {
		return super.with("portCount", condType, portCount);
	}
	
	public Specification<VirtualPlcEntity> withManufacturer(CondType condType, String manufacturer) {
		return super.with("manufacturer", condType, manufacturer);
	}
	
	public Specification<VirtualPlcEntity> withDescription(CondType condType, String description) {
		return super.with("description", condType, description);
	}
	
	public Specification<VirtualPlcEntity> withCreatedBy(CondType condType, String createdBy) {
		return super.with("createdBy", condType, createdBy);
	}
	
	public Specification<VirtualPlcEntity> withCreatedDate(CondType condType, String createdDate) {
		return super.with("createdDate", condType, createdDate);
	}
	
	public Specification<VirtualPlcEntity> withUpdatedBy(CondType condType, String updatedBy) {
		return super.with("updatedBy", condType, updatedBy);
	}
	
	public Specification<VirtualPlcEntity> withUpdatedDate(CondType condType, String updatedDate) {
		return super.with("updatedDate", condType, updatedDate);
	}
}
