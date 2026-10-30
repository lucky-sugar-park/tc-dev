package com.wow.be.tc.plc.connector.melsec;

import java.util.concurrent.CompletableFuture;

import com.wow.be.tc.plc.connector.Payload;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * PLC 와의 TCP 연결 하나 (docs/plc-connection-design.md §5.1).
 *
 * <p>MC 3E 프레임에는 요청과 응답을 짝지을 번호가 없으므로 연결 하나에 진행 중인 요청은 하나뿐이다. 어댑터가 유휴 채널
 * 대기열로 그것을 보장하고, 이 객체는 그 한 요청의 {@link CompletableFuture} 만 들고 있다. 진행 중인 요청이 없을 때 온
 * 응답(시간 초과 뒤 늦게 온 응답 등)은 버린다.
 *
 * <p>Netty 채널은 끊기고 다시 연결될 때마다 바뀐다. 이 객체는 같은 (포트, 로컬 VIP) 자리를 계속 나타낸다.
 */
@Slf4j
final class MelsecChannel {

	private final String plcName;

	private final int port;

	private final String localVip;

	private volatile Channel channel;

	private volatile CompletableFuture<byte[]> pending;

	/** 재연결 시도 회차(성공하면 0) */
	private int reconnectAttempt;

	MelsecChannel(String plcName, int port, String localVip) {
		this.plcName = plcName;
		this.port = port;
		this.localVip = localVip;
	}

	int port() {
		return port;
	}

	String localVip() {
		return localVip;
	}

	Channel channel() {
		return channel;
	}

	boolean isConnected() {
		Channel ch = channel;
		return ch != null && ch.isActive();
	}

	void attach(Channel ch) {
		this.channel = ch;
		this.reconnectAttempt = 0;
	}

	/** 지금 붙어 있는 채널이 {@code ch} 일 때만 떼어 낸다. 이미 새 채널로 바뀌었으면 아무것도 하지 않는다. */
	boolean detachIf(Channel ch) {
		if (this.channel != ch) {
			return false;
		}
		this.channel = null;
		return true;
	}

	int nextReconnectAttempt() {
		return ++reconnectAttempt;
	}

	/** 요청 하나를 시작한다. 빌린 채널에서만 부르므로 이전 요청은 이미 끝나 있다. */
	CompletableFuture<byte[]> begin() {
		CompletableFuture<byte[]> f = new CompletableFuture<>();
		this.pending = f;
		return f;
	}

	void end() {
		this.pending = null;
	}

	/** 연결이 끊겼을 때 기다리던 요청을 실패시킨다 */
	void failPending(Throwable cause) {
		CompletableFuture<byte[]> f = pending;
		if (f != null) {
			f.completeExceptionally(cause);
		}
	}

	void onResponse(byte[] data) {
		CompletableFuture<byte[]> f = pending;
		if (f == null || !f.complete(data)) {
			log.warn("기다리는 요청이 없는 PLC 응답을 버립니다: plc={}, port={}, {} bytes", plcName, port, data != null ? data.length : 0);
		}
	}

	/** 닫는다. closeFuture 리스너가 재연결을 예약한다(어댑터가 멈춘 상태가 아니면). */
	void close() {
		Channel ch = channel;
		if (ch != null) {
			ch.close();
		}
	}

	@Override
	public String toString() {
		return plcName + "@" + localVip + "→" + port;
	}

	/** 이 채널 전용 응답 핸들러. 디코더가 완성한 응답 프레임을 진행 중인 요청에 넘긴다. */
	static final class ResponseHandler extends SimpleChannelInboundHandler<Payload> {

		private final MelsecChannel owner;

		ResponseHandler(MelsecChannel owner) {
			this.owner = owner;
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, Payload msg) {
			owner.onResponse(msg.getData());
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			log.warn("PLC 채널 오류로 연결을 닫습니다: {} — {}", owner, cause.toString());
			ctx.close();
		}
	}
}
