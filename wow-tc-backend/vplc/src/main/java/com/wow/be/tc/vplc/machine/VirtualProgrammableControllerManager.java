package com.wow.be.tc.vplc.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VirtualProgrammableControllerManager {

	private static VirtualProgrammableControllerManager manager;

	private Map<String, VirtualProgrammableController> vplcMap;

	private VirtualProgrammableControllerManager () {
		vplcMap=new HashMap<>();
	}

	public static VirtualProgrammableControllerManager getManager () {
		if(manager==null) {
			manager=new VirtualProgrammableControllerManager();
		}
		return manager;
	}

	public void addVirtualProgrammableController(String id, VirtualProgrammableController vplc) throws VirtualPlcException {
		log.debug("VirtualProgrammableControllerManager.addVirtualProgrammableController(String id, VirtualProgrammableController plc)...");
		this.vplcMap.put(id, vplc);
	}

	public VirtualProgrammableController getVirtualProgrammableController(String id) {
		return vplcMap.get(id);
	}

	public void releaseVirtualProgrammableController(String id) {
		log.debug("");
		try {
			this.vplcMap.remove(id).release();
		} catch (VirtualPlcException e) {
			log.warn("", e);
		};
	}
	
	public int getPlcCount() {
		return vplcMap.size();
	}

	public List<VirtualProgrammableController> getAll() {
		List<VirtualProgrammableController> vplcs=new ArrayList<>();
		for(Iterator<VirtualProgrammableController> iter=vplcMap.values().iterator();iter.hasNext(); ) {
			vplcs.add(iter.next());
		}
		return vplcs;
	}
}
