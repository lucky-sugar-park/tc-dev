package com.wow.be.tc.vplc.transport;

import com.wow.be.tc.vplc.machine.Message;
import com.wow.be.tc.vplc.machine.ResponseMessageCallback;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyResponseMessageCallbackListener implements ResponseMessageCallback {
	
	private MessageFrameHandler handler;
	
	public NettyResponseMessageCallbackListener(MessageFrameHandler handler) {
		this.handler=handler;
	}

	@Override
	public void onResponse(Message message) {
		log.debug("NettyCallbackListener.doCallback(Message message)...");

		Payload payload=new Payload();
		payload.setChannelId(message.getClientChannelId());
		payload.setRawMessage(message.toByteArray());
		payload.setMessage(message);
		
		handler.send(payload);
	}
}
