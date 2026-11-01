package com.wow.be.tc.vplc.melsec.transport;

import java.net.InetSocketAddress;

import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;
import com.wow.be.tc.vplc.melsec.ClientChannel;
import com.wow.be.tc.vplc.melsec.ClientChannelStatusChangeEventHandler;
import com.wow.be.tc.vplc.melsec.VirtualPlcMessageDelegate;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import com.wow.be.tc.vplc.melsec.MelsecTypes;

@Slf4j
@Sharable
public class MessageFrameHandler extends ChannelInboundHandlerAdapter {
	
	private ClientChannelStatusChangeEventHandler channelEventHandler;
	
	private VirtualPlcMessageDelegate messageDelegate;
	
	private FrameFormat messageFrameFormat;
	
	public MessageFrameHandler(VirtualPlcMessageDelegate messageDelegate, FrameFormat messageFrameFormat) {
		this.messageFrameFormat=messageFrameFormat;
		this.messageDelegate=messageDelegate;
		this.channelEventHandler=new ClientChannelStatusChangeEventHandler();
		this.messageDelegate.dispatchChannelEventHandler(channelEventHandler);
	}
	
	@Override
    public void channelActive(ChannelHandlerContext channelHandlerContext) {
		log.info("MessageFrameHandler.channelActive(ChannelHandlerContext channelHandlerContext)...");

		Channel channel=channelHandlerContext.channel();
		InetSocketAddress remoteAddr=(InetSocketAddress)channel.remoteAddress();
		int remotePort=remoteAddr.getPort();
		
		InetSocketAddress serverAddr=(InetSocketAddress)channel.localAddress();
		int serverPort=serverAddr.getPort();
		
		log.info("Server IP: {}, Port: {}", serverAddr.toString(), serverPort);
		log.info("CLIENT CONNECTED: IP: {}, Port: {}", remoteAddr.toString(), remotePort);

		ClientChannel clientChannel=new ClientChannel(channel, messageDelegate.getPlc().getId(), serverPort, channelEventHandler, messageFrameFormat);
		clientChannel.channelActive();
    }
	
	public void send(Payload payload) {
		log.debug("MessageFrameHandler.send(Payload payload)...");
		ClientChannel client=ClientChannel.find(payload.getChannelId());
		Channel channel=client.getChannel();
		try {
			ChannelFuture cf=channel.writeAndFlush(payload);
			cf.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if(!cf.isSuccess()) {
						log.info("Fail to send message: {}", payload.forString());
					} else {
						log.debug("Success to send message: {}", payload.forString());
					}
				}
			});
		} catch (Throwable t) {
			log.warn("Fail to send payload: {}", payload.forString(), t);
		}
	}
	
	@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		log.debug("MessageFrameHandler.channelRead(ChannelHandlerContext ctx, Object msg)...");
		super.channelRead(ctx, msg);
		Payload payload=(Payload)msg;
		// Virtual PLC가 Client로부터의 요청에 대한 응답을 전송할 수 있도록 Channel 정보가 담겨 있는 Callback 클래스를 넘겨줌
		messageDelegate.processMessage(payload.getMessage(), new NettyResponseMessageCallbackListener(this));
	}

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    	log.debug("MessageFrameHandler.channelReadComplete(ChannelHandlerContext ctx)...");
//        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    	log.debug("MessageFrameHandler.exceptionCaught(ChannelHandlerContext ctx, Throwable cause)...");
        cause.printStackTrace();
        super.exceptionCaught(ctx, cause);
    }
    
    public void channelInactive(ChannelHandlerContext ctx) throws java.lang.Exception {
    	log.info("CLIENT DISCONNECTED: {}", ctx.channel().remoteAddress().toString());
    	log.info("Server IP: {}, Port: {}", ctx.channel().localAddress().toString(), ((InetSocketAddress)ctx.channel().localAddress()).getPort());
    	super.channelInactive(ctx);
    	ClientChannel cc=ClientChannel.find(ctx.channel().id().asLongText());
    	if(cc!=null) {
        	cc.channelInActive();
    	}
    }	
}
