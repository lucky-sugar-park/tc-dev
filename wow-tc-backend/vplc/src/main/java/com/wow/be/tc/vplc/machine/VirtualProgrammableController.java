package com.wow.be.tc.vplc.machine;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.PushEventPayload;
import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.MemoryType;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/** 가상 MELSEC PLC(MC 3E·4E) — Netty 로 포트를 열고 MC 메모리를 둔다 */
@Data
@Slf4j
public class VirtualProgrammableController implements VirtualDevice {
	
	private static int NAME_COUNT=0;
	
	private static int DEFAULT_PORT=12345;
	
	private static String BOOSTRAPER_CLASS_NAME="com.wow.be.tc.vplc.transport.VplcNettyCommBootstrapper";
	
	private String id;
	
	private FrameFormat messageFrameFormat;

	private String name;
	
	private String ipAddress;
	
	private PlcStatus status;
	
	private String manufacturer;
	
	private String description;
	
	private Map<String, Memory> memMap;
	
	private int startPort;
	
	private int portCount;
	
	private int portConnLimit;
	
	private VirtualPlcCommBootstrapper bootstraper;
	
	private ClientChannelStatusChangeEventHandler channelEventHandler;
	
	private VirtualPlcMessageDelegate messageDelegate;
	
	public VirtualProgrammableController() {
		this("NO_ID", "NONAMED-"+NAME_COUNT, null, PlcStatus.UNKNOWN, FrameFormat.ASCII, new ArrayList<>(), DEFAULT_PORT, 1, 1);
	}
	
	public VirtualProgrammableController (
			String id, 
			String name, 
			String ipAddress, 
			PlcStatus status, 
			FrameFormat messageFrameFormat, 
			List<MemoryInfo> memInfos, 
			int startPort, 
			int portCount, 
			int connLimit) 
	{
		log.debug("VirtualProgrammableController.VirtualProgrammableController(String id, String name, PlcStatus status, FrameFormat messageFrameFormat, List<MemoryInfo> memInfos, int startPort, int portCount, int connLimit)...");

		this.id=id;
		this.name=name;
		this.ipAddress=ipAddress;
		this.status=status;
		this.messageFrameFormat=messageFrameFormat;
		this.startPort=startPort;
		this.portCount=portCount;
		this.portConnLimit=connLimit;

		memMap=new HashMap<>();
		memInfos.forEach(memInfo->{
			Memory mem=new Memory(memInfo.getMemoryType());
			mem.addWatcher((event)->{
				Executors.newCachedThreadPool().execute(()->{
					// 필요시 메모리 값이 변경되면 UI로 전송해 줄 필요 있음
					PushEventPayload memChangedEvent = new PushEventPayload() {
						@Override
						public String toJson() {
							Gson gson=new GsonBuilder().setPrettyPrinting().create();
							return gson.toJson(event);
						}
					};
					memChangedEvent.setEventKind(VirtualPlcEventKind.VPLC_MEM_VALUE_UPDATED.getName());
					EventPusher.newPusher().push(memChangedEvent);
				});
			});
			memMap.put(memInfo.getMemoryType().getAsciiCode(), mem);
		});

		try {
			this.bootstraper=
					VirtualPlcCommBootstrapper.createVplcCommBoostrapper(
							BOOSTRAPER_CLASS_NAME, 
							ipAddress,
							startPort, 
							portCount, 
							connLimit, 
							this, 
							messageFrameFormat);
		} catch (VirtualPlcException e) {
			log.warn("", e);
		}
	}
	
	@Override
	public Protocol getProtocol() {
		return Protocol.MELSEC_MC;
	}

	/** 이 PLC 포트들에 붙은 채널을 닫는다 — 끊김은 채널 이벤트로 이력에 남는다 */
	@Override
	public int dropConnections() {
		if (channelEventHandler == null) {
			return 0;
		}
		int n = 0;
		for (int p = startPort; p < startPort + portCount; p++) {
			Map<String, ClientChannel> chs = channelEventHandler.getConnectedChannelMap().get(p);
			if (chs == null) {
				continue;
			}
			for (ClientChannel c : new ArrayList<>(chs.values())) {
				c.getChannel().close();
				n++;
			}
		}
		return n;
	}

	public void setChannelEventHandler(ClientChannelStatusChangeEventHandler channelEventHandler) {
		this.channelEventHandler=channelEventHandler;
		PortConnectionLimitEvent event=new PortConnectionLimitEvent(this, "", null, null);
		this.channelEventHandler.propertyChange(event);
	}
	
	public MemoryInfo readBy20Words(MemoryType mType, int start) {
		log.debug("VirtualProgrammableController.readBy20Words(MemoryType mType, int start)...");
		return this.readByWordCount(mType, start, 20);
	}
	
	public MemoryInfo readByWordCount(MemoryType mType, int start, int wordCount) {
		log.debug("VirtualProgrammableController.readByWordCount(MemoryType mType, int start, int wordCount)...");
		MemoryInfo mInfo=new MemoryInfo();
		mInfo.setMemoryType(mType);

		Memory mem=this.memMap.get(mType.name());
		mInfo.setFrameFormat(this.messageFrameFormat);
		mInfo.setStartAddress(start);
		mInfo.setCapacity(mem.getCapacity());
		mInfo.setWordInfos(mem.readWords(start, wordCount));
		return mInfo;
	}

	public byte[] read(int startAddr, int len, MemoryType memType) throws VirtualPlcException {
		log.debug("VirtualProgrammableController.read(int startAddr, int len, MemoryType memType)...");
		Memory mem=memMap.get(memType.getAsciiCode());
		return mem.read(startAddr, len); 
	}
	
	public void write(int startAddr, int wordCount, byte[] data, MemoryType memType) throws VirtualPlcException {
		log.debug("VirtualProgrammableController.write(int startAddr, int wordCount, byte[] data, MemoryType memType)...");
		Memory mem=memMap.get(memType.getAsciiCode());
		if(mem!=null) {
			mem.write(data, startAddr, wordCount);
		}
	}
	
	/** 그 종류의 메모리. 만들지 않았으면 null — MC 요청에 0xC056 으로 답한다 */
	public Memory memory(MemoryType memType) {
		return memMap.get(memType.getAsciiCode());
	}

	public void clear(MemoryType memType) throws VirtualPlcException {
		log.debug("");
		Memory mem=memMap.get(memType.getAsciiCode());
		if(mem!=null) {
			mem.clear();
		}
	}
	
//==========================================================================================

	public void start() throws VirtualPlcException {
		log.debug("VirtualProgrammableController.start()..., PLC Name: {}", this.getName());
		this.bootstraper.ready();
		this.setStatus(PlcStatus.RUNNING);
		this.messageDelegate.startPbox();
		this.propagateEvent(VirtualPlcEventKind.VPLC_STARTED);
	}
	
	public void stop () throws VirtualPlcException {
		log.debug("VirtualProgrammableController.stop ()..., PLC Name: {}", this.getName());
		this.bootstraper.stop();
		this.setStatus(PlcStatus.STOPPED);
		this.messageDelegate.standbyPbox();
		this.propagateEvent(VirtualPlcEventKind.VPLC_STOPPED);
	}
	
	public void release() throws VirtualPlcException {
		log.debug("VirtualProgrammableController.release ()..., PLC Name: {}", this.getName());
		this.stop();
		this.messageDelegate.release();
	}
	
	public void pause () throws VirtualPlcException {
		log.debug("VirtualProgrammableController.pause ()..., PLC Name: {}", this.getName());
		this.setStatus(PlcStatus.PAUSED);
		this.messageDelegate.pausePbox();
		this.propagateEvent(VirtualPlcEventKind.VPLC_PAUSED);
	}
	
	public void resume () throws VirtualPlcException {
		log.debug("VirtualProgrammableController.resume ()..., PLC Name: {}", this.getName());
		this.setStatus(PlcStatus.RUNNING);
		this.messageDelegate.resumePbox();
		this.propagateEvent(VirtualPlcEventKind.VPLC_RESUMED);
	}

	/**
	 * 접속한 UI (웹브라우저)로 PLC의 상태변화를 Push 한다
	 * 
	 * @param eventKind
	 */
	private void propagateEvent(VirtualPlcEventKind eventKind) {
		log.debug("VirtualProgrammableController.propagateEvent(VirtualPlcEventKind eventKind)..., PLC Name: {}", this.getName());
		PushEventPayload vplcEvent=new PushEventPayload() {
			@Override
			public String toJson() {
				// 레거시는 json-smart(JSONObject)를 썼다. 클래스패스에 없어 framework 가 주는 Gson 으로 같은 키를 만든다.
				JsonObject json=new JsonObject();
				json.addProperty("vplcId", getId());
				json.addProperty("vplcName", getName());
				json.addProperty("ipAddress", getIpAddress());
				json.addProperty("status", getStatus().name());
				json.addProperty("timestamp", new Date().toString());

				return json.toString();
			}
		};
		vplcEvent.setEventKind(eventKind.getName());
		EventPusher.newPusher().push(vplcEvent);
	}

	@Data
	@EqualsAndHashCode(callSuper=false)
	public class PortConnectionLimitEvent extends PropertyChangeEvent {

		private static final long serialVersionUID = 1L;
		
		private int startPort;
		
		private int portCount;
		
		private int portConnLimit=1;

		public PortConnectionLimitEvent(Object source, String propertyName, Object oldValue, Object newValue) {
			super(source, propertyName, oldValue, newValue);
			VirtualProgrammableController vplc=(VirtualProgrammableController)source;
			this.startPort=vplc.getStartPort();
			this.portCount=vplc.getPortCount();
			this.portConnLimit=vplc.getPortConnLimit();
		}
	}
}
