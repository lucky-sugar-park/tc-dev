package com.wow.be.tc.plc.connector.melsec;

import com.wow.be.tc.plc.connector.Payload;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyMessageEncoder extends MessageToByteEncoder<Payload> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Payload msg, ByteBuf out) throws Exception {
		log.debug("NettyMessageEncoder.encode(ChannelHandlerContext ctx, Payload msg, ByteBuf out) is called.");
		log.debug("ChannelId: {}", ctx.channel().id());
		byte[] request=msg.getData();
		log.debug("Outstream bytes: {}", (request!=null ? java.util.HexFormat.of().withUpperCase().formatHex(request) : null));
		out.writeBytes(msg.getData());
	}
}
