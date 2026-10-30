package com.wow.be.tc.vplc.management.impl;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class VirtualPlcHistorySpec extends SearchSpec<VirtualPlcHistoryEntity>{

	public Specification<VirtualPlcHistoryEntity> withPlcId(CondType condType, String name) {
		return super.with("plcId", condType, name);
	}

	public Specification<VirtualPlcHistoryEntity> withPlcName(CondType condType, String plcName) {
		return super.with("plcName", condType, plcName);
	}

	public Specification<VirtualPlcHistoryEntity> withStatus(CondType condType, String status) {
		return super.with("status", condType, status);
	}
	
	public Specification<VirtualPlcHistoryEntity> withHistoryContents(CondType condType, String historyContents) {
		return super.with("historyContents", condType, historyContents);
	}
	
	public Specification<VirtualPlcHistoryEntity> withCreatedBy(CondType condType, String createdBy) {
		return super.with("createdBy", condType, createdBy);
	}
	
	public Specification<VirtualPlcHistoryEntity> withCreatedDate(CondType condType, String createdDate) {
		return super.with("createdDate", condType, createdDate);
	}
	
	public Specification<VirtualPlcHistoryEntity> withUpdatedBy(CondType condType, String updatedBy) {
		return super.with("updatedBy", condType, updatedBy);
	}
	
	public Specification<VirtualPlcHistoryEntity> withUpdatedDate(CondType condType, String updatedDate) {
		return super.with("updatedDate", condType, updatedDate);
	}
}
