package com.wow.be.tc.vplc.melsec;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;
import com.wow.be.tc.vplc.melsec.ChannelEvent.ChannelEventName;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class ClientChannel {
	
	private static Map<String, ClientChannel> channelMap;
	
	static {
		channelMap=new HashMap<>();
	}
	
	private String id;
	
	private String vplcId;
	
	private int port;
	
	private String remoteIp;
	
	private Date connectedTimestamp;
	
	private Channel channel;
	
	private FrameFormat messageFrameFormat;
	
	private ClientChannelStatusChangeEventHandler eventHandler;
	
	public ClientChannel(Channel channel, String vplcId, int port, ClientChannelStatusChangeEventHandler eventHandler, FrameFormat messageFrameFormat) {
		this.id=channel.id().asLongText();
		this.vplcId=vplcId;
		this.port=port;
		InetSocketAddress sockAddr=(InetSocketAddress)channel.remoteAddress();
		this.remoteIp=sockAddr.getAddress().getHostAddress();
		this.channel=channel;
		this.connectedTimestamp=new Date();
		this.eventHandler=eventHandler;
		this.messageFrameFormat=messageFrameFormat;
	}

	public void channelActive() {
		log.debug("ClientChannel.channelActive()...");
		channelMap.put(id, this);
		eventHandler.propertyChange(new ChannelEvent(this, ChannelEventName.ACTIVE.name(), this));
	}
	
	public void channelInActive() {
		log.debug("ClientChannel.channelInActive()...");
		
		if(channel != null) channel.close();
		channelMap.remove(id);
		eventHandler.propertyChange(new ChannelEvent(this, ChannelEventName.INACTIVE.name(), this));
	}
	
	public void destroy() {
		log.debug("ClientChannel.destroy()... channelId: {}", this.id);
		
		ChannelFuture cf=this.channel.close();
		log.info("Channel closed: {}", cf.isCancelled());
		channelMap.remove(id);
	}
	
	public static ClientChannel find(String id) {
		return channelMap.get(id);
	}
}
