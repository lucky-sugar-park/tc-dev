package com.wow.be.tc.msg.audit.impl;

import java.util.Date;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Builder
@Entity
@Table(name="EXCHANGED_MESSAGE_AUDIT")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
public class ExchangedMessageAuditEntity extends PersistentEntity<ExchangedMessageAuditInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ID")
	private Long id;

	@Column(name="TRX_ID")
	private String transactionId;

	@Column(name="MSG_ID")
	private String messageId;

	@Column(name="NAME")
	private String name;

	@Column(name="MSG_TYPE")
	private String messageType;

	@Column(name="TIMESTAMP")
	private Date timestamp;

	@Column(name="SENDER")
	private String sender;

	@Column(name="RECEIVER", length=1024)
	private String receiver;

	// 요청·응답 data 전체(JSON) — 길이 상한이 없다
	@Lob
	@Column(name="MSG_JSON", columnDefinition="LONGTEXT")
	private String jsonString;
	
	@Column(name="DESCRIPTION", length=1024)
	private String description;

	/** 실제 호출자 — client:{id}, user:{id}, system. 본문의 SENDER 는 호출자가 쓰는 값이라 따로 둔다(설계 §6.5) */
	@Column(name="PRINCIPAL", length=200)
	private String principal;
	
	public ExchangedMessageAuditEntity() {
		super();
	}

	@Override
	public ExchangedMessageAuditInfo info() {
		ExchangedMessageAuditInfo info=new ExchangedMessageAuditInfo();
		info.setId(this.getId());
		info.setTransactionId(this.getTransactionId());
		info.setMessageId(this.getMessageId());
		info.setName(this.getName());
		info.setMessageType(this.getMessageType());
		info.setTimestamp(this.getTimestamp());
		info.setSender(this.getSender());
		info.setReceiver(this.getReceiver());
		Gson gson=new GsonBuilder().setPrettyPrinting().create();
		info.setDataMap(gson.fromJson(jsonString, new TypeToken<Map<String, String>>(){}.getType()));
		info.setDescription(this.getDescription());
		info.setPrincipal(this.getPrincipal());
		
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());

		return info;
	}

	@Override
	public void from(ExchangedMessageAuditInfo info) {
		// 새 감사 기록의 id 는 0(long 기본값)이다. 0 을 넣으면 Hibernate 가 없는 행을 merge 하려다
		// "Row was already updated or deleted" 로 실패해 감사 로그가 하나도 남지 않으므로 새 행은 null 로 둔다.
		this.setId(info.getId()>0?info.getId():null);
		this.setTransactionId(info.getTransactionId());
		this.setMessageId(info.getMessageId());
		this.setName(info.getName());
		this.setMessageType(info.getMessageType());
		this.setTimestamp(info.getTimestamp());
		this.setSender(info.getSender());
		this.setReceiver(info.getReceiver());
		Gson gson=new GsonBuilder().setPrettyPrinting().create();
		this.setJsonString(gson.toJson(info.getDataMap()));
		this.setDescription(info.getDescription());
		this.setPrincipal(info.getPrincipal());
		
		super.setCreatedBy(info.getCreatedBy());
		super.setCreatedDate(info.getCreatedDate());
		super.setUpdatedBy(info.getUpdatedBy());
		super.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
