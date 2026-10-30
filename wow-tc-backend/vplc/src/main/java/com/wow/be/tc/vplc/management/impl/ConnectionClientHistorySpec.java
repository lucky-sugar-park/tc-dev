package com.wow.be.tc.vplc.management.impl;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class ConnectionClientHistorySpec extends SearchSpec<ConnectionClientHistoryEntity>{

	public Specification<ConnectionClientHistoryEntity> withVplcId(CondType condType, String vplcId) {
		return super.with("vplcId", condType, vplcId);
	}

	public Specification<ConnectionClientHistoryEntity> withPort(CondType condType, String port) {
		return super.with("port", condType, port);
	}

	public Specification<ConnectionClientHistoryEntity> withClientIp(CondType condType, String clientIp) {
		return super.with("clientIp", condType, clientIp);
	}
	
	public Specification<ConnectionClientHistoryEntity> withConnOrDisconn(CondType condType, String connOrDisconn) {
		return super.with("connOrDisconn", condType, connOrDisconn);
	}
	
	public Specification<ConnectionClientHistoryEntity> withTimestamp(CondType condType, String timestamp) {
		return super.with("timestamp", condType, timestamp);
	}
	
	public Specification<ConnectionClientHistoryEntity> withDescription(CondType condType, String description) {
		return super.with("description", condType, description);
	}
	
	public Specification<ConnectionClientHistoryEntity> withCreatedBy(CondType condType, String createdBy) {
		return super.with("createdBy", condType, createdBy);
	}
	
	public Specification<ConnectionClientHistoryEntity> withCreatedDate(CondType condType, String createdDate) {
		return super.with("createdDate", condType, createdDate);
	}
	
	public Specification<ConnectionClientHistoryEntity> withUpdatedBy(CondType condType, String updatedBy) {
		return super.with("updatedBy", condType, updatedBy);
	}
	
	public Specification<ConnectionClientHistoryEntity> withUpdatedDate(CondType condType, String updatedDate) {
		return super.with("updatedDate", condType, updatedDate);
	}
}
