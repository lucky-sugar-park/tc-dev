package com.wow.be.tc.vplc.melsec;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.PushEventPayload;
import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;
import com.wow.be.tc.vplc.melsec.MelsecTypes.MemoryType;
import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import com.wow.be.tc.vplc.core.AbstractVirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcEventKind;
import com.wow.be.tc.vplc.core.memory.AreaMemory;

/**
 * 가상 MELSEC PLC(MC 3E·4E) — Netty 로 포트(시작 포트·포트 수)를 열고 MC 메모리(디바이스 종류별)를 둔다.
 * 상태 규칙은 {@link AbstractVirtualPlc}, 영역 메모리({@link AreaMemory})는 메모리 종류 = 영역(워드 단위, 비트 디바이스도 워드로).
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class MelsecVirtualPlc extends AbstractVirtualPlc implements AreaMemory {
	
	private static int NAME_COUNT=0;
	
	private static int DEFAULT_PORT=12345;
	
	private static String BOOSTRAPER_CLASS_NAME="com.wow.be.tc.vplc.melsec.transport.VplcNettyCommBootstrapper";
	
	private FrameFormat messageFrameFormat;

	private String ipAddress;
	
	private String manufacturer;
	
	private String description;
	
	private Map<String, Memory> memMap;
	
	private int startPort;
	
	private int portCount;
	
	private int portConnLimit;
	
	private VirtualPlcCommBootstrapper bootstraper;
	
	private ClientChannelStatusChangeEventHandler channelEventHandler;
	
	private VirtualPlcMessageDelegate messageDelegate;
	
	public MelsecVirtualPlc() {
		this("NO_ID", "NONAMED-"+NAME_COUNT, null, FrameFormat.ASCII, new ArrayList<>(), DEFAULT_PORT, 1, 1);
	}
	
	public MelsecVirtualPlc (
			String id, 
			String name, 
			String ipAddress, 
			FrameFormat messageFrameFormat, 
			List<MemoryInfo> memInfos, 
			int startPort, 
			int portCount, 
			int connLimit) 
	{
		super(id, name);
		log.debug("MelsecVirtualPlc.MelsecVirtualPlc(String id, String name, FrameFormat messageFrameFormat, List<MemoryInfo> memInfos, int startPort, int portCount, int connLimit)...");

		this.ipAddress=ipAddress;
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
		log.debug("MelsecVirtualPlc.readBy20Words(MemoryType mType, int start)...");
		return this.readByWordCount(mType, start, 20);
	}
	
	public MemoryInfo readByWordCount(MemoryType mType, int start, int wordCount) {
		log.debug("MelsecVirtualPlc.readByWordCount(MemoryType mType, int start, int wordCount)...");
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
		log.debug("MelsecVirtualPlc.read(int startAddr, int len, MemoryType memType)...");
		Memory mem=memMap.get(memType.getAsciiCode());
		return mem.read(startAddr, len); 
	}
	
	public void write(int startAddr, int wordCount, byte[] data, MemoryType memType) throws VirtualPlcException {
		log.debug("MelsecVirtualPlc.write(int startAddr, int wordCount, byte[] data, MemoryType memType)...");
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

	@Override
	protected void doStart() throws VirtualPlcException {
		log.debug("MelsecVirtualPlc.doStart()..., PLC Name: {}", this.getName());
		this.bootstraper.ready();
		this.messageDelegate.startPbox();
	}

	@Override
	protected void doStop() throws VirtualPlcException {
		log.debug("MelsecVirtualPlc.doStop()..., PLC Name: {}", this.getName());
		this.bootstraper.stop();
		this.messageDelegate.standbyPbox();
	}

	@Override
	protected void doRelease() throws VirtualPlcException {
		this.messageDelegate.release();
	}

	/** 일시정지 — 요청을 받되 답하지 않는다(통신 위임이 RUNNING 이 아니면 버린다) */
	@Override
	protected void doPause() throws VirtualPlcException {
		this.messageDelegate.pausePbox();
	}

	@Override
	protected void doResume() throws VirtualPlcException {
		this.messageDelegate.resumePbox();
	}

	// ---- 영역 메모리 — 메모리 종류 = 영역, 워드 단위 ----

	/** 주소를 16진으로 적는 디바이스 */
	private static final java.util.Set<MemoryType> HEX = java.util.EnumSet.of(MemoryType.X, MemoryType.Y, MemoryType.B, MemoryType.W,
		MemoryType.SB, MemoryType.SW, MemoryType.DX, MemoryType.DY);

	/** 비트 디바이스 — 워드로 담고 비트마다 주소 */
	private static final java.util.Set<MemoryType> BIT = java.util.EnumSet.of(MemoryType.X, MemoryType.Y, MemoryType.M, MemoryType.L,
		MemoryType.F, MemoryType.V, MemoryType.B, MemoryType.S, MemoryType.SM, MemoryType.SB, MemoryType.DX, MemoryType.DY, MemoryType.TS,
		MemoryType.TC, MemoryType.SS, MemoryType.SC, MemoryType.CS, MemoryType.CC);

	/**
	 * 디바이스의 마지막 번호(워드 기준). MemoryType 의 경계는 16진 표기 디바이스만 진짜 16진이고 나머지는 10진 숫자를 16진 리터럴로 적어 두었다
	 * (D 0x012287 = 12287) — 그대로 읽는다. 메모리 크기를 넘지 않게.
	 */
	private static int lastAddress(MemoryType t, int capacity) {
		int last;
		try {
			last = HEX.contains(t) ? t.getBoundaryTo() : Integer.parseInt(Integer.toHexString(t.getBoundaryTo()));
		} catch (NumberFormatException e) {
			last = t.getBoundaryTo();
		}
		return Math.min(last, capacity - 1);
	}

	@Override
	public List<Area> areas() {
		List<Area> out = new ArrayList<>();
		for (MemoryType t : MemoryType.values()) {
			Memory m = memMap.get(t.getAsciiCode());
			if (m != null) {
				out.add(new Area(t.getAsciiCode(), t.getAsciiCode().replace("*", ""), Unit.WORD, lastAddress(t, m.getCapacity()) + 1, HEX.contains(t),
					BIT.contains(t)));
			}
		}
		return out;
	}

	private Memory requireMemory(String area) throws VirtualPlcException {
		Memory m = memMap.get(area);
		if (m == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "이 가상 PLC 에 없는 메모리 종류입니다: " + area);
		}
		return m;
	}

	/** 워드 0~65535 — 메모리는 워드마다 낮은 바이트가 앞(MC 와 같다) */
	@Override
	public int[] read(String area, int start, int count) throws VirtualPlcException {
		byte[] b = requireMemory(area).read(start, count);
		int[] out = new int[count];
		for (int i = 0; i < count; i++) {
			out[i] = (b[i << 1] & 0xFF) | (b[(i << 1) + 1] & 0xFF) << 8;
		}
		return out;
	}

	@Override
	public void write(String area, int start, int[] values) throws VirtualPlcException {
		byte[] b = new byte[values.length << 1];
		for (int i = 0; i < values.length; i++) {
			b[i << 1] = (byte) values[i];
			b[(i << 1) + 1] = (byte) (values[i] >> 8);
		}
		requireMemory(area).write(b, start, values.length);
	}

	@Override
	public void clear(String area) throws VirtualPlcException {
		requireMemory(area).clear();
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
			MelsecVirtualPlc vplc=(MelsecVirtualPlc)source;
			this.startPort=vplc.getStartPort();
			this.portCount=vplc.getPortCount();
			this.portConnLimit=vplc.getPortConnLimit();
		}
	}
}
