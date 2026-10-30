package com.wow.be.tc.plc.management.impl;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.utils.SearchSpecs;
import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorInfoStatus;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;
import com.wow.be.tc.plc.management.PlcConfig;
import com.wow.be.tc.plc.management.PlcConnectionEventDbHandler;
import com.wow.be.tc.plc.management.PlcConnectionEventHistInfo;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.management.PlcConnectorManagementService;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultPlcConnectorManagementService implements PlcConnectorManagementService, PersistentService<PlcConnectorInfo, PlcConnectorEntity> {
	
	@Autowired
	private PlcConnectorRepository plcConnectorRepository;
	
	@Autowired
	private PlcConnectionEventHistRepository plcConnectionEventHistRepository;
	
	@Autowired
	private PlcConfig plcConfig;

	@Transactional
	@Override
	public void registerPlcConnector(PlcConnectorInfo plcInfo) {
		log.debug("DefaultPlcConnectorManagementService.registerPlcConnector(PlcConnectorInfo plcInfo)...");
		PlcConnectorEntity entity=new PlcConnectorEntity();
		entity.from(plcInfo);
		entity.setStatus1(PlcConnectorInfoStatus.REGISTERED.name());
		entity.setStatus2(PlcConnectorOperStatus.CONNECTION_TEST_NONE.name());
		plcConnectorRepository.save(entity);
		log.debug("");
	}
	
	@Transactional
	@Override
	public void updatePlcConnector(PlcConnectorInfo plcConnectorInfo) {
		log.debug("DefaultPlcConnectorManagementService.updatePlcConnector(PlcConnectorInfo plcConnectorInfo)...");
		PlcConnectorEntity entity=plcConnectorRepository.findById(plcConnectorInfo.getId()).get();
		entity.from(plcConnectorInfo);
		// perform more sets and save it
		plcConnectorRepository.save(entity);
	}
	
	@Transactional
	@Override
	public void deletePlcConnectorById(long plcConnectorId) {
		log.debug("DefaultPlcConnectorManagementService.deletePlcConnectorById(long plcConnectorId)...");
		plcConnectorRepository.deleteById(plcConnectorId);
	}

	@Transactional
	@Override
	public void deletePlcConnectorByName(String plcConnectorName) {
		log.debug("DefaultPlcConnectorManagementService.deletePlcConnectorByName(String plcConnectorName)...");
		plcConnectorRepository.deleteByName(plcConnectorName);
	}

	@Override
	public PlcConnectorInfo findPlcConnectorById(long id) {
		log.debug("DefaultPlcConnectorManagementService.findPlcConnectorById(long id)...");
		try {
			PlcConnectorInfo info=plcConnectorRepository.findById(id).get().info();
			return info;
		} catch (NoSuchElementException e) {
			return null;
		}
	}
	
	private PlcConnectorEntity findPlcConnectorEntityByName(String name) {
		log.debug("DefaultPlcConnectorManagementService.findPlcConnectorEntityByName(String name)...");
		
		PlcConnectorEntity probe=new PlcConnectorEntity();
		probe.setName(name);
		
		ExampleMatcher matcher = ExampleMatcher.matchingAny();
		matcher=matcher.withMatcher("name", ExampleMatcher.GenericPropertyMatchers.exact());
		Example<PlcConnectorEntity> example=Example.of(probe, matcher);

		return plcConnectorRepository.findOne(example).get();
	}

	@Override
	public PlcConnectorInfo findPlcConnectorByName(String name) {
		log.debug("DefaultPlcConnectorManagementService.findPlcConnectorByName(String name)...");

		try {
			PlcConnectorEntity entity=this.findPlcConnectorEntityByName(name);
			return entity.info();
		} catch (NoSuchElementException e) {
			return null;
		}
	}
	
	public List<PlcConnectorInfo> searchAllPlcConnectores() {
		log.debug("DefaultPlcConnectorManagementService.searchAllPlcConnectores()...");
		return infos(plcConnectorRepository.findAll());
	}
	
	public List<PlcConnectorInfo> searchAllPlcConnectores(PageInfo pageInfo) {
		log.debug("DefaultPlcConnectorManagementService.searchAllPlcConnectores(PageInfo pageInfo)...");
		Sort.Direction dir=pageInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
		PageRequest prequest=PageRequest.of(pageInfo.getPage(), pageInfo.getSize(), dir, pageInfo.getSortBy());
		return infos(plcConnectorRepository.findAll(prequest).getContent());
	}

	@Override
	public List<PlcConnectorInfo> searchPlcConnectores(Condition cond) {
		log.debug("DefaultPlcConnectorManagementService.searchPlcConnectores(SearchCondition cond)...");
		PlcConnectorSpec specs=new PlcConnectorSpec();
		List<PlcConnectorEntity> plcConnectorEntityList=null;
		Specification<PlcConnectorEntity> specification=SearchSpecs.create(specs, cond);

		if(cond.getPageInfo()==null) {
			 plcConnectorEntityList=plcConnectorRepository.findAll(specification);
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			plcConnectorEntityList=plcConnectorRepository.findAll(specification, prequest).getContent();
		}

		return infos(plcConnectorEntityList);
	}

	@Override
	public List<PlcConnectorInfo> searchPlcConnectores(Condition cond, PageInfo pageInfo) {
		log.debug("DefaultPlcConnectorManagementService.searchPlcConnectores(SearchCondition cond, PageInfo pageInfo)...");
		cond.setPageInfo(pageInfo);
		return searchPlcConnectores(cond);
	}
	
	@Transactional
	@Override
	public boolean publishPlcConnector(long id) {
		log.debug("DefaultPlcConnectorManagementService.publishPlcConnector(long id)...");
		PlcConnectorEntity entity=this.plcConnectorRepository.findById(id).get();
		return manageAndControlPlcConnector(entity, true, PlcConnectorInfoStatus.PUBLISHED, PlcConnectorOperStatus.RUNNING, PlcConnectorOperStatus.ERROR);
	}
	
	@Transactional
	@Override
	public boolean publishPlcConnectorByName(String plcName) {
		log.debug("DefaultPlcConnectorManagementService.publishPlcConnectorByName(String plcName)...");
		PlcConnectorEntity entity=this.findPlcConnectorEntityByName(plcName);
		return manageAndControlPlcConnector(entity, true, PlcConnectorInfoStatus.PUBLISHED, PlcConnectorOperStatus.RUNNING, PlcConnectorOperStatus.ERROR);
	}
	
	private boolean manageAndControlPlcConnector(PlcConnectorEntity entity, boolean published, PlcConnectorInfoStatus status1, PlcConnectorOperStatus successState, PlcConnectorOperStatus failState) {
		log.debug("DefaultPlcConnectorManagementService.manageAndControlPlcConnector(PlcEntity entity, boolean published, String status1, String successState, String failState)...");
		PlcConnectorInfo pInfo=entity.info();
		pInfo.setPublished(published);
		pInfo.setStatus1(status1);
		try {
			switch(successState) {
			case RUNNING :
				Properties props=new Properties();
				props.put("id", pInfo.getId());
				props.put("name", pInfo.getName());
				props.put("className", pInfo.getAdapterClassName());
				props.put("hostIp", pInfo.getHostIp());
				props.put("targetPorts", pInfo.getPorts());
				props.put("localVips", pInfo.getConnectionVips());
				props.put("operStatus", successState.name());
				props.put("asyncUse", pInfo.isAsyncRequestUse());
				props.put("asyncTimerStartDelay", pInfo.getAsyncTimerStartDelay());
				props.put("asyncTimerRunInterval", pInfo.getAsyncTimerRunInterval());
				props.put("messageFrameFormat", pInfo.getMessageFrameFormat().getName());
				props.put("headerReqTemplate", pInfo.getCommProtocolHeadersResTemplate());
				props.put("headerResTemplate", pInfo.getCommProtocolHeadersResTemplate());
				props.put("plcRetryCount", plcConfig.getRetryCount());
				props.put("plcRetryIntervalRate", plcConfig.getRetryIntervalRate());
				props.put("plcRetryIntervalIncrement", plcConfig.getRetryIntervalIncrement());
				
				PlcConnectionEventListener connEventListener=new PlcConnectionEventDbHandler(this);
				props.put("connectionListener", connEventListener);
				
				PlcAdapterManager.getPlcManager().loadProgrammableControllerAdapter(props);
				break;
			case STOPPED :
				PlcAdapterManager.getPlcManager().releaseProgrammableControllerAdapter(entity.getName());
				break;
			case PAUSED:
				PlcAdapterManager.getPlcManager().pauseProgrammableControllerAdapter(entity.getName());
				break;
			case RESUMED :
				PlcAdapterManager.getPlcManager().resumeProgrammableControllerAdapter(entity.getName());
				break;
			default:
				break;
			}
			pInfo.setStatus2(successState);
			entity.from(pInfo);
			plcConnectorRepository.save(entity);
			return true;
		} catch (ToolControlException e) {
			log.warn("", e);
			entity.setStatus2(failState.name());
			plcConnectorRepository.save(entity);
			return false;
		}
	}
	
	@Transactional
	@Override
	public boolean releasePlcConnector(long id) {
		log.debug("DefaultPlcConnectorManagementService.releasePlcConnector(long id)...");
		PlcConnectorEntity entity=this.plcConnectorRepository.findById(id).get();
		return manageAndControlPlcConnector(entity, false, PlcConnectorInfoStatus.RELEASED, PlcConnectorOperStatus.STOPPED, PlcConnectorOperStatus.ERROR);
	}
	
	@Override
	public boolean releasePlcConnectorByName(String plcName) {
		log.debug("DefaultPlcConnectorManagementService.releasePlcConnectorByName(String plcName)...");
		PlcConnectorEntity entity=this.findPlcConnectorEntityByName(plcName);
		return manageAndControlPlcConnector(entity, false, PlcConnectorInfoStatus.RELEASED, PlcConnectorOperStatus.STOPPED, PlcConnectorOperStatus.ERROR);
	}
	
	/**
	 * No use currently
	 */
	@Transactional
	@Override
	public boolean pausePlcConnector(long id) {
		log.debug("DefaultPlcConnectorManagementService.pausePlcConnector(long id)...");
		PlcConnectorEntity entity=this.plcConnectorRepository.findById(id).get();
		return manageAndControlPlcConnector(entity, true, PlcConnectorInfoStatus.RELEASED, PlcConnectorOperStatus.PAUSED, PlcConnectorOperStatus.ERROR);
	}
	
	/**
	 * No use currently
	 */
	@Transactional
	@Override
	public boolean pausePlcConnectorByName(String plcConnectorName) {
		log.debug("DefaultPlcConnectorManagementService.pausePlcConnectorByName(String plcConnectorName)...");
		PlcConnectorEntity entity=this.findPlcConnectorEntityByName(plcConnectorName);
		return manageAndControlPlcConnector(entity, true, PlcConnectorInfoStatus.RELEASED, PlcConnectorOperStatus.PAUSED, PlcConnectorOperStatus.ERROR);
	}
	
	/**
	 * No use currently
	 */
	@Transactional
	@Override
	public boolean resumePlcConnector(long id) {
		log.debug("DefaultPlcConnectorManagementService.resumePlcConnector(long id)...");
		PlcConnectorEntity entity=this.plcConnectorRepository.findById(id).get();
		return manageAndControlPlcConnector(entity, true, PlcConnectorInfoStatus.RELEASED, PlcConnectorOperStatus.RESUMED, PlcConnectorOperStatus.ERROR);
	}
	
	/**
	 * No use currently
	 */
	@Transactional
	@Override
	public boolean resumePlcConnectorByName(String plcConnectorName) {
		log.debug("DefaultPlcConnectorManagementService.resumePlcConnectorByName(String plcConnectorName)...");
		PlcConnectorEntity entity=this.findPlcConnectorEntityByName(plcConnectorName);
		return manageAndControlPlcConnector(entity, true, PlcConnectorInfoStatus.RELEASED, PlcConnectorOperStatus.RESUMED, PlcConnectorOperStatus.ERROR);
	}

	@Override
	public Map<String, String> testConnection(long id) {
		log.debug("DefaultPlcConnectorManagementService.testConnection(long id)...");
		PlcConnectorInfo plcInfo=this.findPlcConnectorById(id);
		return testConnection(plcInfo);
	}

	@Override
	public Map<String, String> testConnectionByName(String name) {
		log.debug("DefaultPlcConnectorManagementService.testConnectionByName(String name)...");
		PlcConnectorInfo plcInfo=this.findPlcConnectorByName(name);
		return testConnection(plcInfo);
	}
	
	private Map<String, String> testConnection(PlcConnectorInfo pInfo) {
		log.debug("DefaultPlcConnectorManagementService.testConnection(PlcInfo pInfo) is called.");
		return testConnection(pInfo.getHostIp(), pInfo.getPorts().get(0), pInfo.getConnectionVips().get(0));
	}
	
	private Map<String, String> testConnection(String ip, int port, String localIp) {
		log.debug("DefaultPlcConnectorManagementService.testConnection(String ip, int port) is called.");
		
		EventLoopGroup workerGroup=new NioEventLoopGroup();
		Bootstrap b=new Bootstrap();
		b.group(workerGroup);
		b.channel(NioSocketChannel.class);
		b.option(ChannelOption.TCP_NODELAY, true);
		b.option(ChannelOption.SO_KEEPALIVE, true);
		b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);
		b.option(ChannelOption.SO_REUSEADDR, true);
		b.handler(new ChannelInitializer<SocketChannel>() {	 
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new MessageToByteEncoder<byte[]>() {
					@Override
					protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) throws Exception {
						out.writeBytes(msg);
					}
				});
				ch.pipeline().addLast(new ByteToMessageDecoder() {
					@Override
					protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
						byte frames[]=new byte[in.readableBytes()];
						out.add(in.readBytes(frames));
					}
				});
				ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
			}
		});
		Map<String, String> result=new HashMap<>();
		int ASSIGN_DYNAMIC_PORT_RANGE=0;
		ChannelFuture cf=null;
		try {
			if(localIp==null) {
				cf=b.connect(new InetSocketAddress(ip, port)).sync(); 
			} else {
				cf=b.connect(
						new InetSocketAddress(ip, port), 
						new InetSocketAddress(localIp, ASSIGN_DYNAMIC_PORT_RANGE)).sync();
			}
			cf.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture cf) throws Exception {
					if(cf.isSuccess()) {
						result.put("result", PlcConnectorOperStatus.CONNECTION_TEST_OK.name());
						log.debug("");
					} else {
						result.put("result", PlcConnectorOperStatus.CONNECTION_TEST_ERROR.name());
						result.put("cause", cf.cause()!=null?cf.cause().getMessage():"ChannelFuture is not successful.");
						result.put("description", "It would be over PLC's connection limit or PLC itself problem.");
						log.warn("");
					}
					cf.channel().disconnect();
					
					cf.channel().closeFuture().sync();
					workerGroup.shutdownGracefully();
				}
			});
		} catch (Throwable e) {
			result.put("result", PlcConnectorOperStatus.CONNECTION_TEST_ERROR.name());
			result.put("cause", e.getMessage());
			result.put("description", "It would be network problem, wrong host ip / port number or PLC's down.");

			log.warn("Fail to connect to host: {}, port: {}", ip, port, e);
		}
		
		return result;
	}

	@Override
	public Map<String, String> testConnectionByIpAddress(String ip, int port) {
		log.debug("DefaultPlcConnectorManagementService.testConnectionByIpAddress(String ip, int port) is called.");
		return testConnection(ip, port, null);
	}

	@Override
	@Transactional
	public void savePlcConnectionEventHistory(PlcConnectionEventHistInfo pcehInfo) {
		log.debug("");
		PlcConnectionEventHistEntity entity=new PlcConnectionEventHistEntity();
		entity.from(pcehInfo);
		plcConnectionEventHistRepository.save(entity);
	}

	@Override
	public List<PlcConnectionEventHistInfo> searchPlcConnectionEventHist(Condition cond, PageInfo pageInfo) {
		log.debug("");
		PlcConnectionEventHistSpec specs=new PlcConnectionEventHistSpec();
		List<PlcConnectionEventHistEntity> plcConnectionEventHistEntityList=null;
		Specification<PlcConnectionEventHistEntity> specification=SearchSpecs.create(specs, cond);

		if(cond.getPageInfo()==null) {
			plcConnectionEventHistEntityList=plcConnectionEventHistRepository.findAll(specification);
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			plcConnectionEventHistEntityList=plcConnectionEventHistRepository.findAll(specification, prequest).getContent();
		}
		
		List<PlcConnectionEventHistInfo> infoList=new ArrayList<>();
		plcConnectionEventHistEntityList.forEach(entity->{
			infoList.add(entity.info());
		});
		return infoList;
	}
	
	@Transactional
	@Override
	public long deletePlcConnectionEventHistories(Date timestamp) {
		log.debug("");
		long delCount=plcConnectionEventHistRepository.countByTimestamp(timestamp);
		plcConnectionEventHistRepository.deleteByTimestamp(timestamp);
		return delCount;
	}
}
