package com.wow.be.tc.vplc.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.extern.slf4j.Slf4j;

/** 게시된(메모리에 올라온) 가상 PLC 들 — 종류와 관계없이 아이디로 */
@Slf4j
public class VirtualProgrammableControllerManager {

	private static VirtualProgrammableControllerManager manager;

	private Map<String, VirtualDevice> vplcMap;

	private VirtualProgrammableControllerManager () {
		vplcMap=new HashMap<>();
	}

	public static VirtualProgrammableControllerManager getManager () {
		if(manager==null) {
			manager=new VirtualProgrammableControllerManager();
		}
		return manager;
	}

	public synchronized void addVirtualProgrammableController(String id, VirtualDevice vplc) throws VirtualPlcException {
		log.debug("VirtualProgrammableControllerManager.addVirtualProgrammableController(String id, VirtualProgrammableController plc)...");
		this.vplcMap.put(id, vplc);
	}

	/** 게시된 가상 MELSEC PLC — 없거나 다른 종류면 null */
	public VirtualProgrammableController getVirtualProgrammableController(String id) {
		return vplcMap.get(id) instanceof VirtualProgrammableController v ? v : null;
	}

	/** 게시된 가상 PLC(종류 무관) — 없으면 null */
	public VirtualDevice get(String id) {
		return vplcMap.get(id);
	}

	public synchronized void releaseVirtualProgrammableController(String id) {
		log.debug("");
		VirtualDevice d=this.vplcMap.remove(id);
		if(d==null) return;
		try {
			d.release();
		} catch (VirtualPlcException e) {
			log.warn("", e);
		};
	}
	
	public int getPlcCount() {
		return vplcMap.size();
	}

	public List<VirtualDevice> getAll() {
		List<VirtualDevice> vplcs=new ArrayList<>();
		for(Iterator<VirtualDevice> iter=vplcMap.values().iterator();iter.hasNext(); ) {
			vplcs.add(iter.next());
		}
		return vplcs;
	}
}
