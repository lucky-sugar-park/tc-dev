package com.wow.be.tc.plc.connector.melsec;

import java.util.Date;
import java.util.List;

import com.wow.be.tc.plc.connector.Payload;
import com.wow.be.tc.plc.protocol.melsec.McFrames;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * PLC 응답 바이트를 프레임 하나씩 잘라 넘긴다. 3E·4E 는 서브헤더로 가린다({@link McFrames#responseLength}) — 같은 연결에서
 * RAW 인터페이스(3E)와 4E 설정의 POINTS 인터페이스가 섞여도 된다.
 *
 * <p>예전 ASCII 해석은 버퍼 배열을 직접 읽고(직접 버퍼면 실패), 길이 문자를 거꾸로 10진으로 읽고, 길이 검사가 늘 통과해 ASCII
 * 응답을 한 번도 제대로 자르지 못했다(결함 M5).
 */
@Slf4j
public class NettyMessageDecoder extends ByteToMessageDecoder {

	private final boolean ascii;

	private final String messageFrameFormat;

	public NettyMessageDecoder(String messageFrameFormat) {
		super();
		this.messageFrameFormat = messageFrameFormat;
		this.ascii = "ASCII".equals(messageFrameFormat);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		int available = in.readableBytes();
		if (available == 0) {
			return;
		}
		byte[] head = new byte[Math.min(available, 64)];
		in.getBytes(in.readerIndex(), head);
		int len = McFrames.responseLength(head, head.length, ascii);
		if (len == 0 && head.length < available) {
			// 길이 칸까지는 64바이트 안에 있다 — 여기 오면 프레임이 64바이트보다 길고 아직 덜 왔다
			byte[] all = new byte[available];
			in.getBytes(in.readerIndex(), all);
			len = McFrames.responseLength(all, available, ascii);
		}
		if (len < 0) {
			log.warn("PLC 응답 머리가 아닌 바이트 {}개를 버립니다({}): {}", available, messageFrameFormat, ctx.channel().remoteAddress());
			in.skipBytes(available);
			return;
		}
		if (len == 0) {
			return;  // 덜 왔다
		}
		byte[] frame = new byte[len];
		in.readBytes(frame);
		Payload payload = new Payload();
		payload.setTimestamp(new Date().getTime());
		payload.setFrameFormat(messageFrameFormat);
		payload.setData(frame);
		out.add(payload);
	}
}
