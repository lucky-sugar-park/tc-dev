package com.wow.be.tc.msg.audit.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
// 색인: 기간 검색·보관 정리(docs/data-retention.md) — migration/016
@Table(name="EXCHANGED_MESSAGE_AUDIT", indexes={
	@Index(name="IX_EMA_TIMESTAMP", columnList="TIMESTAMP")
})
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
public class ExchangedMessageAuditEntity extends PersistentEntity<ExchangedMessageAuditInfo> {

	private static final long serialVersionUID = 1L;

	private static final ObjectMapper JSON = new ObjectMapper();

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
		info.setDataMap(parseJson(jsonString));
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

	/**
	 * 저장된 JSON 을 값 타입 그대로 읽는다. 예전에는 {@code Map<String, String>} 으로 읽어 숫자가 문자열이 되고
	 * 중첩 객체(DELIVERY 의 response)는 읽지 못했다. 재전송은 이 값을 그대로 다시 보낸다(docs/redelivery-design.md §2).
	 */
	static Map<String, Object> parseJson(String json) {
		if (json == null || json.isBlank() || "null".equals(json.trim())) {
			return new HashMap<>();
		}
		try {
			Map<String, Object> map = JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
			return map != null ? map : new HashMap<>();
		} catch (JsonProcessingException e) {
			// 객체가 아닌 JSON 등 — 화면에서 원문을 볼 수 있게 둔다
			Map<String, Object> raw = new HashMap<>();
			raw.put("raw", json);
			return raw;
		}
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
