package com.wow.be.tc.plc.connector.tcp;

import com.wow.be.tc.plc.connector.Payload;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/** 요청 프레임을 그대로 보낸다 — 프로토콜마다 프레임은 드라이버가 이미 만들었다 */
@Slf4j
public class FrameEncoder extends MessageToByteEncoder<Payload> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Payload msg, ByteBuf out) throws Exception {
		byte[] request = msg.getData();
		if (log.isDebugEnabled()) {
			log.debug("PLC 요청 {}: {}", ctx.channel().id(), request != null ? java.util.HexFormat.of().withUpperCase().formatHex(request) : null);
		}
		out.writeBytes(request);
	}
}
