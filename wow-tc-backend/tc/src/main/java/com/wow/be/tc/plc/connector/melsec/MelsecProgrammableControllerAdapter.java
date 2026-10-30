package com.wow.be.tc.plc.connector.melsec;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.PlcReqAndResHolder;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcRequestCallback;
import com.wow.be.tc.plc.PlcResponse;
import com.wow.be.tc.plc.connector.Payload;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;
import com.wow.be.tc.plc.connector.PlcConnectorEvent.EventType;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.utils.ByteUtil.Endian;
import com.wow.be.tc.utils.ByteUtil;
import com.wow.be.tc.utils.TimerUtil;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class MelsecProgrammableControllerAdapter implements ProgrammableControllerAdapter {
	
	private static int ASSIGN_DYNAMIC_PORT_RANGE=0;
	
	private long id;

	private String name;
	
	private String hostIp;
	
	private List<Integer> tports;
	
	private List<String> localVips;
	
	private boolean asyncUse=true;
	
	private long asyncTimerStartDelay;
	
	private long asyncTimerRunInterval;
	
	private String messageFrameFormat;
	
//	private Map<String, Object> headerReqTemplate;
//	
//	private Map<String, Object> headerResTemplate;
	
	private Deque<PlcReqAndResHolder> q;
	
	private Map<String, PlcRequestCallback> reqCallbacks;
	
	private Timer timer;
	
	private TimerTask qworker;
	
	private EventLoopGroup workerGroup;
	
	private int CF_INDEX=0;
	
	private List<ChannelFuture> channelFutures;
	
	private Map<Integer, Bootstrap> bootstraps;
	
	private NettyMessageFrameHandler clientHandler;
	
	private PlcConnectorOperStatus operStatus;
	
	private PlcConnectionEventListener pceListener;
	
	private int retryCount;
	
	private long retryIntervalRate;
	
	private boolean retryIntervalIncrement;
	
	public MelsecProgrammableControllerAdapter() {
		this("BINARY");
	}
	
	public MelsecProgrammableControllerAdapter(String messageFrameFormat) {
		log.debug("");
		this.q=new ConcurrentLinkedDeque<>();
		// concurrent 문제해결을 위함
		this.channelFutures=new CopyOnWriteArrayList<>();
		this.bootstraps=new HashMap<>();
		this.messageFrameFormat=messageFrameFormat;
	}

	@Override
	public void pauseAsyncUse() {
		log.debug("");
		this.setAsyncUse(false);
	}

	@Override
	public void resumeAsyncUse() {
		log.debug("");
		this.setAsyncUse(true);
	}
	
	private void prepareNetwork (int count) throws ToolControlException {
		log.info("MelsecProgrammableControllerAdapter.prepareNetwork ()...");
		log.info("PLC Id: {}, PLC Name: {}.", this.id, this.name);

		workerGroup=new NioEventLoopGroup();
		this.clientHandler=new NettyMessageFrameHandler();

		// "PLC IP + Port" 조합으로 클라이언트 1개만 수용할 수 있음. 
		// 연결실패시에 retry 또는 port를 변경해서 연결하는 로직 보완 필요함
		int cnt=Math.min(tports.size(), localVips.size());
		int successCount=0;
		for(int i=0;i<cnt;i++) {
			Bootstrap b=new Bootstrap();
			b.group(workerGroup);
			b.channel(NioSocketChannel.class);
			b.option(ChannelOption.TCP_NODELAY, true);
			// keep alive 신호를 주기적으로 주고 받음
			b.option(ChannelOption.SO_KEEPALIVE, true);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
			b.option(ChannelOption.SO_REUSEADDR, true);
			b.handler(new ChannelInitializer<SocketChannel>() {	 
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new NettyMessageEncoder());
					ch.pipeline().addLast(new NettyMessageDecoder(messageFrameFormat));
					ch.pipeline().addLast(clientHandler);
				}
			});

			try {
				ChannelFuture cf=b.connect(
						new InetSocketAddress(hostIp, tports.get(i)), 
						new InetSocketAddress(localVips.get(i), ASSIGN_DYNAMIC_PORT_RANGE)).sync();
				this.channelFutures.add(cf);
				
				PlcConnectorEvent connectEvent=new PlcConnectorEvent();
				connectEvent.setPlcConnectorId(getId());
				connectEvent.setPlcConnectorName(getName());
				connectEvent.setOperStatus(this.operStatus);
				connectEvent.setActiveChannelCount(channelFutures.size());
				connectEvent.setCause(cf.cause()!=null ? cf.cause().getMessage() : "UNKNOWN");
				connectEvent.setType(EventType.CONNECT);
				
				String remote=cf.channel().remoteAddress().toString();
				String local =cf.channel().localAddress().toString();
				connectEvent.setRemoteIp(remote.substring(0,remote.lastIndexOf(":")));
				connectEvent.setRemotePort(Integer.parseInt(remote.substring(remote.lastIndexOf(":")+1)));
				connectEvent.setLocalIp(local.substring(0,local.lastIndexOf(":")));
				connectEvent.setLocalPort(Integer.parseInt(local.substring(local.lastIndexOf(":")+1)));
				connectEvent.setTimestamp(new Date());
				connectEvent.setDescription(channelFutures.size()+" EA connections are available.");

				pceListener.onEvent(connectEvent);
				
				cf.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture>() {
					@Override
					public void operationComplete(ChannelFuture future) throws Exception {
						log.warn(
								"Channel closed with plc-connector: {}, local: {}, remote: {}, cause: {}", 
								name, 
								future.channel().localAddress(), 
								future.channel().remoteAddress(), 
								future.cause()!=null ? future.cause().getMessage() : "UNKNOWN"
						);
						synchronized (this) {
							for(ChannelFuture chfu:channelFutures) {
								if(chfu.channel().id().equals(future.channel().id())) {
									channelFutures.remove(chfu);
									break;
								}
							}
						
							PlcConnectorEvent disconnectEvent=new PlcConnectorEvent();
							disconnectEvent.setPlcConnectorId(getId());
							disconnectEvent.setPlcConnectorName(getName());
							disconnectEvent.setOperStatus(operStatus);
							disconnectEvent.setActiveChannelCount(channelFutures.size());
							disconnectEvent.setCause(future.cause()!=null ? future.cause().getMessage() : "UNKNOWN");
							disconnectEvent.setType(EventType.DISCONNECT);
						
							String remote=future.channel().remoteAddress().toString();
							String local =future.channel().localAddress().toString();
							disconnectEvent.setRemoteIp(remote.substring(0,remote.lastIndexOf(":")));
							disconnectEvent.setRemotePort(Integer.parseInt(remote.substring(remote.lastIndexOf(":")+1)));
							disconnectEvent.setLocalIp(local.substring(0,local.lastIndexOf(":")));
							disconnectEvent.setLocalPort(Integer.parseInt(local.substring(local.lastIndexOf(":")+1)));
							disconnectEvent.setTimestamp(new Date());
							disconnectEvent.setDescription(channelFutures.size()+" EA connections are available.");
						
							pceListener.onEvent(disconnectEvent);
						}
					}});
				
				this.bootstraps.put(tports.get(i), b);
				successCount++;
			} catch (Exception e) {
				log.warn("Fail to connect from PLC-Connector: {}, to host: {}", this.getName(), hostIp+":"+this.tports.get(i), e);
			}
		}
		if(successCount==0) {
			log.info("Retrying to connect PLC: {}, try count: {}", this.name, count);
			if(count > this.retryCount) {
				throw new ToolControlException("연결된 채널이 없는 PLC 커넥터입니다: " + this.getName());
			}
			if(this.retryIntervalIncrement) {
				TimerUtil.waitFor(this.retryIntervalRate * count);	
			} else {
				TimerUtil.waitFor(this.retryIntervalRate);
			}

			prepareNetwork(count+1);
		}
	}
	
	private void prepareTimer () {
		log.info("");
		this.qworker=new TimerTask() {
			@Override
			public void run() {
				synchronized(this) {
					if(!q.isEmpty()) {
						if(Thread.interrupted()) return;
						log.debug("Fetching PLC request from queue...");

						List<PlcReqAndResHolder> reqList=new ArrayList<>();
						while(!q.isEmpty()) reqList.add(q.poll());
						
						reqList.forEach(holder->{
							PlcRequest  req=holder.getRequest();
							PlcResponse res=holder.getResponse();
							try {
								request(req, res);
								PlcRequestCallback reqCallback=reqCallbacks.get(req.getReqId());
								if(reqCallback!=null) {
									reqCallback.callbackRequest(res);
								}
							} catch (ToolControlException e) {
								log.warn("Fail to execute PLC request: {}", req.getReqId(), e);
							}
						});
						TimerUtil.waitWithSleep(500);
					}
				}
			}
		};
		// 데몬으로 실행함 (Main thread 종류되면 timer도 종료되어야 함)
		this.timer=new Timer(true);
		long delay=asyncTimerStartDelay, interval=asyncTimerRunInterval;
		this.timer.scheduleAtFixedRate(qworker, delay, interval);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void ready(long id, String name, Properties props) throws ToolControlException {
		log.info("MelsecProgrammableControllerAdapter.ready(long id, String name, Properties props)...");
		this.id=id;
		this.name=name;
		this.hostIp=props.getProperty("hostIp");
		this.tports=(List)props.get("targetPorts");
		this.localVips=(List)props.get("localVips");
		this.asyncUse=(Boolean)props.get("asyncUse");
		this.messageFrameFormat=props.getProperty("messageFrameFormat");
		this.asyncTimerStartDelay=(Long)props.get("asyncTimerStartDelay");
		this.asyncTimerRunInterval=(Long)props.get("asyncTimerRunInterval");
		this.pceListener=(PlcConnectionEventListener)props.get("connectionListener");
		this.retryCount=(Integer)props.get("plcRetryCount");
		this.retryIntervalRate=(Long)props.get("plcRetryIntervalRate");
		this.retryIntervalIncrement=(Boolean)props.get("plcRetryIntervalIncrement");
//		this.headerReqTemplate = (Map)props.get("headerReqTemplate");
//		this.headerResTemplate = (Map)props.get("headerResTemplate");
		this.operStatus=PlcConnectorOperStatus.UNKNOWN;
		
		if(this.retryCount==0) this.retryCount = 5;
		else if(this.retryCount==-1) this.retryCount = Integer.MAX_VALUE;
		
		if(this.retryIntervalRate==0 || this.retryIntervalRate==-1) {
			this.retryIntervalRate = 1000;
		}

		this.start();
	}

	@Override
	public void request(PlcRequest req, PlcResponse res) throws ToolControlException {
		log.debug("MelsecProgrammableControllerAdapter.request(PlcRequest req, PlcResponse res)...");
		if(this.operStatus==PlcConnectorOperStatus.PAUSED || this.operStatus==PlcConnectorOperStatus.UNKNOWN) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "요청할 수 없는 운영 상태입니다: " + this.operStatus);
		}

		byte response[]=readOrWrite(req.getReqId(), req.convertRequest());
		res.splitHeaderAndReplyData(response);
		int resultCode=ByteUtil.bytesToInt((byte[])res.getReplyHeaders().get("resultCode"), Endian.LITTLE);
		res.setResultCode(resultCode);
		res.setResultDescription(resultCode==0?"SUCCESS":"ERROR");
	}

	@Override
	public void request(PlcReqAndResHolder reqAndResHolder, PlcRequestCallback reqCallback) throws ToolControlException {
		log.debug("MelsecProgrammableControllerAdapter.request(PlcReqAndResHolder reqAndResHolder, PlcRequestCallback reqCallback)...");
		if(!asyncUse || this.operStatus==PlcConnectorOperStatus.PAUSED || this.operStatus==PlcConnectorOperStatus.UNKNOWN) {
			throw new ToolControlException("비동기 요청을 쓰지 않는 커넥터이거나 요청할 수 없는 운영 상태입니다: 비동기 " + this.asyncUse + ", 상태 " + this.operStatus);
		}

		reqCallbacks.put(reqAndResHolder.getRequest().getReqId(), reqCallback);
		q.add(reqAndResHolder);
	}
	
	/**
	 * Round Robin 방식으로 PLC 연결 adapter를 가져옴
	 * - 동일 IP의 PLC에 대해서 (port 수 또는 local address virtual IP수 중 최소 값) 만큼의 channel 생성됨 
	 * 
	 * @return: ChannelFuture
	 */
	private synchronized ChannelFuture getChannelFuture() {
		if(channelFutures.isEmpty()) return null;

		if(CF_INDEX>=channelFutures.size()) CF_INDEX=0;
		return channelFutures.get(CF_INDEX++);
	}
	
	/**
	 * read/write 요청을 한 후에, PLC로부터 넘어온 결과를 return 한다
	 * 
	 * @return: byte[], subHeader-netNumber-plcNum-IOnum-respDataLen-resultCode-(None | RespData | ErrorDetail)
	 * @throws ToolControlException
	 */
	private byte[] readOrWrite(String reqId, byte[] reqData) throws ToolControlException {
		log.debug("MelsecProgrammableControllerAdapter.readOrWrite(byte[] reqData)...");

		ChannelFuture cf=getChannelFuture();
		if(cf==null) {
			// Channel이 하나도 없으면 예외 처리함
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 와 연결된 채널이 없습니다.");
		}
		
		// 요청을 처리할 때에, 연결된 1 ~ N개의 채널 중에서 무작위로 선택해서 처리하므로 
		// response listener를 동적으로 생성/삭제 하는 것이 바람직함
		synchronized(this) {
			String channelId=cf.channel().id().asLongText();
			NettyResponse response=new NettyResponse();
			this.clientHandler.addResponseListener(channelId, new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					log.info("Response from PLC is arrived.");

					byte[] respData=(byte[])(evt.getNewValue());
					log.info("Channel Id: {}, Response: {}", channelId, java.util.HexFormat.of().withUpperCase().formatHex(respData));

					response.setArrived(true);
					response.setData(respData);
					clientHandler.removeResponseListener(channelId);
				}
			});

			Payload payload=new Payload();
			payload.setTimestamp(new Date().getTime());
			payload.setData(reqData);
			payload.setFrameFormat(this.messageFrameFormat);

			try {
				cf.channel().writeAndFlush(payload).sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			long current=new Date().getTime(), timeout=20000;
			while(response.isArrived()==false && new Date().getTime()-current<timeout) {
				TimerUtil.waitWithSleep(50);
			}

			if(response.isArrived()==false) {
				throw ToolControlException.of(ToolControlException.PLC_TIMEOUT, "PLC 응답 시간이 초과되었습니다(" + timeout + "ms): " + reqId);
			}
			return response.getData();
		}		
	}
	
	// No use - 현재 connection을 테스트 할 때에 netty로 직접 연결 테스트하도록 되어 있음
	@Override
	public boolean testConnection() throws ToolControlException {
		return true;
	}

	/**
	 * No use currently
	 */
	@Override
	public void pause() {
		log.info("");
		this.operStatus=PlcConnectorOperStatus.PAUSED;
	}

	/**
	 * No use currently
	 */
	@Override
	public void resume() {
		log.info("");
		this.operStatus=PlcConnectorOperStatus.RESUMED;
	}

	@Override
	public void stop() {
		log.info("");
		this.operStatus=PlcConnectorOperStatus.STOPPED;
		List<String> localAddresses=new ArrayList<>();
		for(Iterator<ChannelFuture> iter=channelFutures.iterator(); iter.hasNext(); ) {
			ChannelFuture cf=iter.next();
			localAddresses.add(cf.channel().localAddress().toString());
			cf.channel().close();
		}

		// 이 함수는 기존에 수행 중인 Task가 끝나기를 기다리지 않음-deprecated된 이유임.
		// 여기서는 기존에 수행중인 Task를 기다릴 필요 없음
		// shutdownGracefully를 사용하지 않은 이유는, shutdownGracefully 함수가 너무 늦게 끝나면서 plc connector 상태가 꼬이는 현상 때문임
		this.workerGroup.shutdownNow();
		PlcConnectorEvent event=new PlcConnectorEvent();
		event.setPlcConnectorId(getId());
		event.setPlcConnectorName(getName());
		event.setOperStatus(operStatus);
		event.setActiveChannelCount(channelFutures.size());
		event.setType(EventType.SHUTDOWN);
		event.setRemoteIp(hostIp);
		event.setLocalIp(localAddresses.toString());
		event.setCause("STOP_REQUEST");
		event.setDescription(channelFutures.size()+" EA connections are avaialble.");
		event.setTimestamp(new Date());

		pceListener.onEvent(event);				
	}
	

	@Override
	public void start() throws ToolControlException {
		log.info("");
		this.operStatus=PlcConnectorOperStatus.RUNNING;
		
		prepareNetwork(1);
		prepareTimer();

		List<String> remoteAddresses=new ArrayList<>(), localAddresses=new ArrayList<>();
		this.channelFutures.forEach(cf->{
			remoteAddresses.add(cf.channel().remoteAddress().toString());
			localAddresses.add(cf.channel().localAddress().toString());
		});
		PlcConnectorEvent event=new PlcConnectorEvent();
		event.setPlcConnectorId(this.getId());
		event.setPlcConnectorName(this.getName());
		event.setOperStatus(this.operStatus);
		event.setActiveChannelCount(this.channelFutures.size());
		event.setType(EventType.START);
		event.setRemoteIp(remoteAddresses.toString());
		event.setLocalIp(localAddresses.toString());
		event.setCause("START_REQUEST");
		event.setDescription(channelFutures.size()+" EA connections are established.");
		event.setTimestamp(new Date());

		pceListener.onEvent(event);
	}
	
	@Override
	public PlcRequest newPlcRequestInstance() {
		return new MelsecPlcRequest();
	}

	@Override
	public PlcResponse newPlcResponseInstance() {
		return new MelsecPlcResponse();
	}
	
	@Override
	public void destroy() {
		if(q!=null) this.q.clear();
		if(this.reqCallbacks!=null) this.reqCallbacks.clear();
		if(this.tports!=null) this.tports.clear();
		if(this.localVips!=null) this.localVips.clear();
		if(this.channelFutures!=null) this.channelFutures.clear();
		this.timer.cancel();
		this.qworker.cancel();
	}
}
