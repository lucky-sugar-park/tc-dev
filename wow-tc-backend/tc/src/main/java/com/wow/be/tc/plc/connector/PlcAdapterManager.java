package com.wow.be.tc.plc.connector;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;

import com.wow.be.tc.LegacyClassNames;
import com.wow.be.tc.ToolControlException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlcAdapterManager {

	private static final PlcAdapterManager plcManager = new PlcAdapterManager();
	
	private Map<String, ProgrammableControllerAdapter> adapterMap;
	
	private PlcAdapterManager() {
		adapterMap=new ConcurrentHashMap<>();
	}
	
	public static PlcAdapterManager getPlcManager() {
		return plcManager;
	}
	
	public void set(String name, ProgrammableControllerAdapter plcAdapter) {
		adapterMap.put(name, plcAdapter);
	}
	
	/** 올라와 있는 어댑터 수(헬스 체크용) */
	public int size() {
		return adapterMap.size();
	}

	/** 이 인스턴스에 올라와 있는 어댑터들 */
	public Collection<ProgrammableControllerAdapter> adapters() {
		return List.copyOf(adapterMap.values());
	}

	public ProgrammableControllerAdapter get(String name) {
		return adapterMap.get(name);
	}
	
	public void remove(String name) {
		log.info("");
		adapterMap.remove(name);
	}
	
	/**
	 * 어댑터를 만들어 연결한다. 같은 이름의 어댑터가 이미 돌고 있으면 아무것도 하지 않는다(게시 멱등) — 다시 만들면 옛 연결이
	 * PLC 슬롯을 붙잡은 채 새 연결이 거부된다. 설정을 바꿔 다시 올리려면 해제한 뒤 게시한다.
	 * 연결에 실패하면 목록에 넣지 않는다.
	 */
	public synchronized void loadProgrammableControllerAdapter(Properties props) throws ToolControlException {
		log.debug("PlcAdapterManager.loadProgrammableControllerAdapter(Properties props)...");
		long id=(Long)props.get("id");
		String name=(String)props.get("name");
		String className=(String)props.get("className");
		if(adapterMap.containsKey(name)) {
			log.info("이미 게시된 PLC 커넥터라 다시 연결하지 않습니다: {}", name);
			return;
		}
		String operStatus=props.getProperty("operStatus");
		if(!"RUNNING".equals(operStatus)) {
			return;
		}
		ProgrammableControllerAdapter adapter=createPlcAdapterInstance(className);
		adapter.ready(id, name, props);
		adapterMap.put(name, adapter);
	}

	/** 앱 종료 — 모든 어댑터의 연결을 이벤트 없이 닫는다(DB 상태는 그대로) */
	public void shutdownAllQuietly() {
		adapterMap.values().forEach(adapter -> {
			try {
				adapter.shutdownQuietly();
			} catch (RuntimeException e) {
				log.warn("PLC 어댑터를 닫지 못했습니다: {}", adapter.getName(), e);
			}
		});
		adapterMap.clear();
	}

	public synchronized void releaseProgrammableControllerAdapter(String name) throws ToolControlException {
		log.debug("PlcAdapterManager.releaseProgrammableControllerAdapter(String name)...");
		ProgrammableControllerAdapter adapter=this.get(name);
		if(adapter!=null) {
			adapter.stop();
			adapter.destroy();
			this.remove(name);
		}
	}
	
	public void pauseProgrammableControllerAdapter(String name) throws ToolControlException {
		log.debug("PlcAdapterManager.pauseProgrammableControllerAdapter(String name)...");
		this.get(name).pause();
	}
	
	public void startProgrammableControllerAdapter(String name) throws ToolControlException {
		log.debug("PlcAdapterManager.startProgrammableControllerAdapter(String name)...");
		this.get(name).start();
	}
	
	public void resumeProgrammableControllerAdapter(String name) throws ToolControlException {
		log.debug("PlcAdapterManager.resumeProgrammableControllerAdapter(String name)...");
		this.get(name).resume();
	}
	
	private ProgrammableControllerAdapter createPlcAdapterInstance(String className) throws ToolControlException {
		log.debug("PlcAdapterManager.createPlcAdapterInstance(String className)...");
		try {
			return (ProgrammableControllerAdapter) LegacyClassNames.forName(className).getDeclaredConstructor().newInstance();
		} catch (InstantiationException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 어댑터를 만들 수 없습니다: " + className, e);
		} catch (IllegalAccessException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 어댑터를 만들 수 없습니다: " + className, e);
		} catch (IllegalArgumentException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 어댑터를 만들 수 없습니다: " + className, e);
		} catch (InvocationTargetException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 어댑터를 만들 수 없습니다: " + className, e);
		} catch (NoSuchMethodException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 어댑터를 만들 수 없습니다: " + className, e);
		} catch (SecurityException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 어댑터를 만들 수 없습니다: " + className, e);
		} catch (ClassNotFoundException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 어댑터를 만들 수 없습니다: " + className, e);
		}
	}
}
