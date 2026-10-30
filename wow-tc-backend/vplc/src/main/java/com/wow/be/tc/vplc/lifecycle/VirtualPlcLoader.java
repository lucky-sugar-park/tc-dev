package com.wow.be.tc.vplc.lifecycle;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.Types.MemoryType;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.ClientConnectionEventDispatcher;
import com.wow.be.tc.vplc.machine.MemoryInfo;
import com.wow.be.tc.vplc.machine.VirtualProgrammableController;
import com.wow.be.tc.vplc.machine.VirtualProgrammableControllerManager;
import com.wow.be.tc.vplc.management.ConnectionClientEventDbHandler;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;
import com.wow.be.tc.vplc.manipulation.VirtualPlcManipulationService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 기동 때 DB 의 가상 PLC 목록을 메모리로 올린다. 마지막 상태가 RUNNING 이던 PLC 는 바로 시작한다.
 */
@Component
@Slf4j
public class VirtualPlcLoader {
	
	@Autowired
	private ConnectionClientEventDbHandler cceventDBHandler;

	@Autowired
	private VirtualPlcManagementService vplcManagementService;
	
	@Autowired
	private VirtualPlcManipulationService vplcManipulationService;
	
	@PostConstruct
	private void loadVirtualPlcs() {
		log.debug("VirtualPlcLoader.loadVirtualPlcs()...");
		try {
			List<VirtualPlcInfo> vplcList=vplcManagementService.searchAllVirtualPlc();
			if(vplcList==null) return;
			
			vplcList.forEach(vplcInfo->{
				PlcStatus status=vplcInfo.getStatus();
				if(status==PlcStatus.PUBLISHED || status==PlcStatus.PAUSED || status==PlcStatus.RUNNING || status==PlcStatus.STOPPED) {
					try {
						vplcInfo.setMemInfos(extractMemList(vplcInfo.getMemoryTypeList()));
						// RELEASED 또는 REGISTERED 상태인 PLC는 메모리로 로드하지 않음
						vplcManipulationService.publishVirtualPlc(vplcInfo);
						VirtualProgrammableController vplc=
								VirtualProgrammableControllerManager.getManager().getVirtualProgrammableController(vplcInfo.getId());
						vplc.setStatus(vplcInfo.getStatus());
						// DB 상에서의 마지막 상태가 Running 중이었었던 Virtual PLC민 start 시킴
						if(vplcInfo.getStatus()==PlcStatus.RUNNING) {
							vplcManipulationService.startVirtualPlc(vplcInfo.getId());
						}
					} catch (VirtualPlcException e) {
						log.warn("");
					}
				}
			});
			this.readyClientConnectionEventHandler();
		} catch (VirtualPlcException e) {
			log.warn("Fail to load VirtualPlcs. System would be shutdown.", e);
			System.exit(0);
		}
	}
	
	private List<MemoryInfo> extractMemList(List<String> memTypeList) {
		List<MemoryInfo> memories=new ArrayList<>();
		memTypeList.forEach(memType->{
			MemoryInfo mem=new MemoryInfo();
			mem.setMemoryType(MemoryType.toMemoryType(memType));
			memories.add(mem);
		});
		return memories;
	}
	
	private void readyClientConnectionEventHandler () {
		log.debug("");
		ClientConnectionEventDispatcher.getDispatcher().addEventListener(cceventDBHandler);
	}
}
