package com.wow.be.tc.msg.audit;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class ExchangedMessageAuditInfo extends AbstractInfo {

	private long id;

	private String transactionId;

	private String messageId;

	/**
	 * opcode
	 */
	private String name;

	/**
	 * REQUEST_READ, REQUEST_WRITE, RESPONSE, PUSH
	 */
	private String messageType;

	private Date timestamp;

	private String sender;

	private String receiver;

	private Map<String, Object> dataMap;
	
	private String description;

	public ExchangedMessageAuditInfo() {
		dataMap=new HashMap<>();
	}

	public void addData(String key, String value) {
		dataMap.put(key, value);
	}

	public String jsonDataMap(boolean pretty) {
		Gson gson=pretty?new GsonBuilder().setPrettyPrinting().create():new GsonBuilder().create();
		return gson.toJson(dataMap);
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
