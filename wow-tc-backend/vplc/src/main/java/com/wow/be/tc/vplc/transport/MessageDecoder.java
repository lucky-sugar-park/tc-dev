package com.wow.be.tc.vplc.transport;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.machine.McMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageDecoder extends ByteToMessageDecoder {

	private FrameFormat messageFrameFormat;

	public MessageDecoder(FrameFormat messageFrameFormat) {
		log.info("MessageDecoder.MessageDecoder(FrameFormat messageFrameFormat)..., {}", messageFrameFormat.name());
		this.messageFrameFormat=messageFrameFormat;
	}

	/**
	 * 요청을 프레임 하나씩 자른다 — 3E·4E 는 서브헤더로 가린다({@link McCodec#requestLength}). 해석은 {@link McCodec#parse},
	 * 처리는 {@code McResponder}. 요청 머리가 아니면 받은 바이트를 버린다.
	 */
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		boolean ascii = messageFrameFormat == FrameFormat.ASCII;
		int available = in.readableBytes();
		byte[] buf = new byte[available];
		in.getBytes(in.readerIndex(), buf);
		int len = McCodec.requestLength(buf, available, ascii);
		if (len < 0) {
			log.warn("MC 요청 머리가 아닌 바이트 {}개를 버립니다({}): {}", available, messageFrameFormat, ctx.channel().remoteAddress());
			in.skipBytes(available);
			return;
		}
		if (len == 0) {
			return;
		}
		byte[] frame = new byte[len];
		in.readBytes(frame);
		McCodec.McRequest request;
		try {
			request = McCodec.parse(frame, ascii);
		} catch (RuntimeException e) {
			log.warn("MC 요청을 해석하지 못해 버립니다: {} — {}", ctx.channel().remoteAddress(), e.toString());
			return;
		}
		McMessage message = new McMessage(request, null);
		message.setClientChannelId(ctx.channel().id().asLongText());
		message.setFormat(messageFrameFormat);

		Payload payload = new Payload();
		payload.setTransactionId(UUID.randomUUID().toString());
		payload.setMessageId(UUID.randomUUID().toString());
		payload.setChannelId(ctx.channel().id().asLongText());
		payload.setTimestamp(new Date().getTime());
		payload.setRawMessage(frame);
		payload.setMessage(message);
		message.setTransactionId(payload.getTransactionId());
		message.setMessageId(payload.getMessageId());
		out.add(payload);
	}
}
