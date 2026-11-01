package com.wow.be.tc.vplc.melsec.transport;

import com.wow.be.tc.vplc.melsec.Message;

import lombok.Data;

@Data
public class Payload {
	
	private String transactionId;
	
	private String messageId;
	
	private long timestamp;
	
	private String channelId;

	private byte[] rawMessage;

	private Message message;
	
	public String forString() {
		return message.forString();
	}
}
