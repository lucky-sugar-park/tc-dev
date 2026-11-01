package com.wow.be.tc.vplc.melsec.transport;

import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import com.wow.be.tc.vplc.melsec.MelsecTypes;

@Slf4j
public class MessageEncoder extends MessageToByteEncoder<Payload> {
	
	private FrameFormat messageFrameFormat;
	
	public MessageEncoder(FrameFormat messageFrameFormat) {
		this.messageFrameFormat=messageFrameFormat;
		log.info("MessageFrameFormat: {}", messageFrameFormat.name());
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, Payload msg, ByteBuf out) throws Exception {
		log.debug("MessageEncoder.encode(ChannelHandlerContext ctx, Payload msg, ByteBuf out)...");
		log.debug("TransactionId: {}, ChannelId: {}, MessageFrameFormat: {}", msg.getTransactionId(), msg.getChannelId(), messageFrameFormat.name());

		out.writeBytes(msg.getMessage().toByteArray());
	}
}
