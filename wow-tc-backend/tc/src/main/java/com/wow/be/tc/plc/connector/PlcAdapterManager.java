package com.wow.be.tc.plc.connector;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.wow.be.tc.LegacyClassNames;
import com.wow.be.tc.ToolControlException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlcAdapterManager {

	private static PlcAdapterManager plcManager;
	
	private Map<String, ProgrammableControllerAdapter> adapterMap;
	
	private PlcAdapterManager() {
		adapterMap=new HashMap<>();
	}
	
	public static PlcAdapterManager getPlcManager() {
		if(plcManager==null) {
			plcManager=new PlcAdapterManager();
		}
		return plcManager;
	}
	
	public void set(String name, ProgrammableControllerAdapter plcAdapter) {
		adapterMap.put(name, plcAdapter);
	}
	
	public ProgrammableControllerAdapter get(String name) {
		return adapterMap.get(name);
	}
	
	public void remove(String name) {
		log.info("");
		adapterMap.remove(name);
	}
	
	public void loadProgrammableControllerAdapter(Properties props) throws ToolControlException {
		log.debug("PlcAdapterManager.loadProgrammableControllerAdapter(Properties props)...");
		long id=(Long)props.get("id");
		String name=(String)props.get("name");
		String className=(String)props.get("className");
		ProgrammableControllerAdapter adapter=createPlcAdapterInstance(className);
		adapterMap.put(name, adapter);
		
		String operStatus=props.getProperty("operStatus");
		if("RUNNING".equals(operStatus)) {
			adapter.ready(id, name, props);
		}
	}
	
	public void releaseProgrammableControllerAdapter(String name) throws ToolControlException {
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
