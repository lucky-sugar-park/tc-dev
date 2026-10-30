package com.wow.be.tc.plc.connector.melsec;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import com.wow.be.tc.plc.connector.Payload;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Sharable
public class NettyMessageFrameHandler extends ChannelInboundHandlerAdapter {
	
	private Map<String, PropertyChangeListener> responseListeners;
	
	public NettyMessageFrameHandler () {
		this.responseListeners=new HashMap<>();
	}
	
	public void addResponseListener(String id, PropertyChangeListener respListener) {
		this.responseListeners.put(id, respListener);
	}
	
	public void removeResponseListener(String id) {
		this.responseListeners.remove(id);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		log.debug("NettyMessageFrameHandler.channelRead(ChannelHandlerContext ctx, Object msg)...");
		PropertyChangeListener respListener=responseListeners.get(ctx.channel().id().asLongText());
		// null이면 비정상이므로 무시함
		if(respListener==null) return;
		PropertyChangeEvent event=new PropertyChangeEvent(this, "", null, ((Payload)msg).getData());
		respListener.propertyChange(event);
		super.channelRead(ctx, msg);
	}
	
	@Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		log.info("CHANNEL CONNECTED: {}", ctx.channel().remoteAddress().toString());
		super.channelActive(ctx);
	}
	
	@Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    	log.debug("");
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    	log.debug("");
        super.exceptionCaught(ctx, cause);
    }
    
    public void channelInactive(ChannelHandlerContext ctx) throws java.lang.Exception {
    	log.info("CHANNEL DISCONNECTED: {}", ctx.channel().remoteAddress().toString());
    	super.channelInactive(ctx);
    }	
}
