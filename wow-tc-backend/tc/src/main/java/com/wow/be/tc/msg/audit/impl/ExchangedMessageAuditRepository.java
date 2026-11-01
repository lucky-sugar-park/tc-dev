package com.wow.be.tc.msg.audit.impl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangedMessageAuditRepository extends JpaRepository<ExchangedMessageAuditEntity, Long>, JpaSpecificationExecutor<ExchangedMessageAuditEntity> {
	
	/** 재전송 — 전달 기록의 원본 응답 찾기(정상 응답은 MSG_ID 가 같다) */
	public List<ExchangedMessageAuditEntity> findByMessageIdAndMessageType(String messageId, String messageType);

	/** 재전송 — 디스패치 예외로 만든 실패 응답은 MSG_ID 가 달라 거래 ID·인터페이스로 찾는다 */
	public List<ExchangedMessageAuditEntity> findByTransactionIdAndNameAndMessageType(String transactionId, String name, String messageType);

//	public List<ExchangedMessageAuditEntity> findById(Specification<ExchangedMessageAuditEntity> spec, Rangeable rangeable);
}
