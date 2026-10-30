package com.wow.be.tc.msg.audit.impl;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class ExchangedMessageAuditSpec extends SearchSpec<ExchangedMessageAuditEntity> {

	public Specification<ExchangedMessageAuditEntity> withTransactionId(CondType condType, String transactionId) {
		return super.with("transactionId", condType, transactionId);
	}
	
	public Specification<ExchangedMessageAuditEntity> withTransactionIds(CondType condType, List<String> transactionIds) {
		return super.with("transactionId", condType, transactionIds);
	}

	public Specification<ExchangedMessageAuditEntity> withName(CondType condType, String name) {
		return super.with("name", condType, name);
	}
	
	public Specification<ExchangedMessageAuditEntity> withNames(CondType condType, List<String> names) {
		return super.with("name", condType, names);
	}

	public Specification<ExchangedMessageAuditEntity> withMessageId(CondType condType, String messageId) {
		return super.with("messageId", condType, messageId);
	}
	
	public Specification<ExchangedMessageAuditEntity> withMessageIds(CondType condType, List<String> messageIds) {
		return super.with("messageId", condType, messageIds);
	}

	public Specification<ExchangedMessageAuditEntity> withMessageType(CondType condType, String messageType) {
		return super.with("messageType", condType, messageType);
	}
	
	public Specification<ExchangedMessageAuditEntity> withMessageTypes(CondType condType, List<String> messageTypes) {
		return super.with("messageType", condType, messageTypes);
	}
	
	public Specification<ExchangedMessageAuditEntity> withTimestamp(CondType condType, String timestamp) {
		return super.with("timestamp", condType, timestamp);
	}
	
	public Specification<ExchangedMessageAuditEntity> withSender(CondType condType, String sender) {
		return super.with("sender", condType, sender);
	}
	
	public Specification<ExchangedMessageAuditEntity> withSenders(CondType condType, List<String> senders) {
		return super.with("sender", condType, senders);
	}
	
	public Specification<ExchangedMessageAuditEntity> withReceiver(CondType condType, String receiver) {
		return super.with("receiver", condType, receiver);
	}
	
	public Specification<ExchangedMessageAuditEntity> withReceivers(CondType condType, List<String> receivers) {
		return super.with("receiver", condType, receivers);
	}
	
	public Specification<ExchangedMessageAuditEntity> withJsonString(CondType condType, String jsonString) {
		return super.with("jsonString", condType, jsonString);
	}
	
	public Specification<ExchangedMessageAuditEntity> withJsonStrings(CondType condType, List<String> jsonStrings) {
		return super.with("jsonString", condType, jsonStrings);
	}
	
	public Specification<ExchangedMessageAuditEntity> withDescription(CondType condType, String description) {
		return super.with("description", condType, description);
	}
	
	public Specification<ExchangedMessageAuditEntity> withDescriptions(CondType condType, List<String> descriptions) {
		return super.with("description", condType, descriptions);
	}
	
	public Specification<ExchangedMessageAuditEntity> withCreatedBy(CondType condType, String createdBy) {
		return super.with("createdBy", condType, createdBy);
	}
	
	public Specification<ExchangedMessageAuditEntity> withCreatedDate(CondType condType, String createdDate) {
		return super.with("createdDate", condType, createdDate);
	}
	
	public Specification<ExchangedMessageAuditEntity> withUpdatedBy(CondType condType, String updatedBy) {
		return super.with("updatedBy", condType, updatedBy);
	}
	
	public Specification<ExchangedMessageAuditEntity> withUpdatedDate(CondType condType, String updatedDate) {
		return super.with("updatedDate", condType, updatedDate);
	}
}