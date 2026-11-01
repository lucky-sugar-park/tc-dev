package com.wow.be.tc.vplc.melsec.transport;

import java.util.ArrayList;
import java.util.List;

import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.melsec.VirtualPlcCommBootstrapper;
import com.wow.be.tc.vplc.melsec.VirtualPlcMessageDelegate;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import com.wow.be.tc.vplc.melsec.MelsecTypes;

@Slf4j
public class VplcNettyCommBootstrapper extends VirtualPlcCommBootstrapper {

	private EventLoopGroup parentGroup;

	private EventLoopGroup childGroup;

	private List<ChannelFuture> channelFutureList;

	public VplcNettyCommBootstrapper(VirtualPlcMessageDelegate messageProcessor) {
		this(null, FrameFormat.BINARY, 12345, 1, 1, messageProcessor);
	}

	public VplcNettyCommBootstrapper(String ipAddress, FrameFormat frameFormat, int startPort, int portCount, int connLimit, VirtualPlcMessageDelegate messageProcessor) {
		super(ipAddress, frameFormat, startPort, portCount, connLimit, messageProcessor);
		this.channelFutureList=new ArrayList<>();
	}

	public void ready() throws VirtualPlcException {
		log.debug("VirtualPlcCommBootstraper.ready()...");
		
		this.parentGroup = new NioEventLoopGroup();
		this.childGroup  = new NioEventLoopGroup();

		ServerBootstrap sb = new ServerBootstrap();
		sb.group(parentGroup, childGroup);
		sb.channel(NioServerSocketChannel.class);
		
		MessageFrameHandler messageHandler=new MessageFrameHandler(messageDelegate, messageFrameFormat);
		sb.childHandler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new MessageDecoder(messageFrameFormat));
				ch.pipeline().addLast(new MessageEncoder(messageFrameFormat));
				ch.pipeline().addLast(messageHandler);
			}
		});

		for(int port=startPort; port<startPort+portCount; port++) {
			try {
				ChannelFuture cf=sb.bind(super.getIpAddress(), port).sync();
				channelFutureList.add(cf);
				log.info("Port: {}, success: {}", port, cf.isSuccess());
			} catch (Throwable t) {
				log.warn("Fail to bind to port: {}", port, t);
			}
		}
	}

	public void stop() {
		if(parentGroup!=null) parentGroup.shutdownGracefully();
		if(childGroup !=null) childGroup.shutdownGracefully();
		
		for(ChannelFuture cf:channelFutureList) {
			try {
				cf.channel().close().sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		channelFutureList.clear();
	}
}
