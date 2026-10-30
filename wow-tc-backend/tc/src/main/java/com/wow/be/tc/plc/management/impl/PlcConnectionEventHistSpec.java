package com.wow.be.tc.plc.management.impl;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class PlcConnectionEventHistSpec extends SearchSpec<PlcConnectionEventHistEntity> {

	public Specification<PlcConnectionEventHistEntity> withPlcConnectorId(CondType condType, long plcConnectorId) {
		return super.with("plcConnectorId", condType, plcConnectorId);
	}

	public Specification<PlcConnectionEventHistEntity> withPlcConnectorName(CondType condType, String plcConnectorName) {
		return super.with("plcConnectorName", condType, plcConnectorName);
	}

	public Specification<PlcConnectionEventHistEntity> withEventType(CondType condType, String eventType) {
		return super.with("eventType", condType, eventType);
	}
	
	public Specification<PlcConnectionEventHistEntity> withEventTypes(CondType condType, List<String> eventTypes) {
		return super.with("eventType", condType, eventTypes);
	}

	public Specification<PlcConnectionEventHistEntity> withStatus1(CondType condType, String status1) {
		return super.with("status1", condType, status1);
	}
	
	public Specification<PlcConnectionEventHistEntity> withStatus1s(CondType condType, String status1s) {
		return super.with("status1", condType, status1s);
	}
	
	public Specification<PlcConnectionEventHistEntity> withStatus12(CondType condType, String status2) {
		return super.with("status2", condType, status2);
	}
	
	public Specification<PlcConnectionEventHistEntity> withStatus2s(CondType condType, String status2s) {
		return super.with("status2", condType, status2s);
	}
	
	public Specification<PlcConnectionEventHistEntity> withTimestamp(CondType condType, Date timestamp) {
		return super.with("timestamp", condType, timestamp);
	}	
}