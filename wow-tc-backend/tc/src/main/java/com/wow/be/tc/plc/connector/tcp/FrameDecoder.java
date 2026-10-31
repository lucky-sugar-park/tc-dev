package com.wow.be.tc.plc.connector.tcp;

import java.util.Date;
import java.util.List;

import com.wow.be.tc.plc.connector.Payload;
import com.wow.be.tc.plc.protocol.WireProtocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * PLC 응답 바이트를 프레임 하나씩 잘라 넘긴다. 어디서 자르는지는 프로토콜이 안다({@link WireProtocol#responseLength}) —
 * MELSEC 은 서브헤더로 3E·4E 를 가리고, Modbus 는 MBAP 길이 칸으로 자른다.
 *
 * <p>예전 MELSEC 전용 디코더의 ASCII 해석은 버퍼 배열을 직접 읽고(직접 버퍼면 실패), 길이 문자를 거꾸로 10진으로 읽고, 길이
 * 검사가 늘 통과해 ASCII 응답을 한 번도 제대로 자르지 못했다(결함 M5).
 */
@Slf4j
public class FrameDecoder extends ByteToMessageDecoder {

	/** 길이 칸은 이 안에 있다 — 프레임이 이보다 길고 덜 왔으면 그때 전체를 본다 */
	private static final int HEAD = 64;

	private final WireProtocol wire;

	public FrameDecoder(WireProtocol wire) {
		this.wire = wire;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		while (in.readableBytes() > 0) {
			int available = in.readableBytes();
			byte[] head = new byte[Math.min(available, HEAD)];
			in.getBytes(in.readerIndex(), head);
			int len = wire.responseLength(head, head.length);
			if (len == 0 && head.length < available) {
				byte[] all = new byte[available];
				in.getBytes(in.readerIndex(), all);
				len = wire.responseLength(all, available);
			}
			if (len < 0) {
				log.warn("PLC 응답 머리가 아닌 바이트 {}개를 버립니다: {}", available, ctx.channel().remoteAddress());
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
			payload.setData(frame);
			out.add(payload);
		}
	}
}
