package com.wow.be.tc.vplc.lifecycle;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.connection.ClientConnectionEventDispatcher;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;
import com.wow.be.tc.vplc.management.ConnectionClientEventDbHandler;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;
import com.wow.be.tc.vplc.manipulation.VirtualPlcManipulationService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 기동 때 DB 의 가상 PLC 목록을 메모리로 올리고 마지막 상태로 되살린다 — RUNNING 은 시작, PAUSED 는 시작 뒤 일시정지, STOPPED 는 정지.
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
			// 붙어 있던 연결은 지난 실행의 것 — 지우고 새로 센다
			vplcManagementService.resetConnectedClients();
			List<VirtualPlcInfo> vplcList=vplcManagementService.searchAllVirtualPlc();
			if(vplcList==null) return;
			
			vplcList.forEach(vplcInfo->{
				PlcStatus status=vplcInfo.getStatus();
				if(status==PlcStatus.PUBLISHED || status==PlcStatus.PAUSED || status==PlcStatus.RUNNING || status==PlcStatus.STOPPED) {
					try {
						// RELEASED 또는 REGISTERED 상태인 PLC는 메모리로 로드하지 않음
						vplcManipulationService.publishVirtualPlc(vplcInfo);
						// DB 의 마지막 상태로 — RUNNING 은 시작, PAUSED 는 시작 뒤 일시정지(예전에는 상태 글자만 붙여 포트가 닫혀 있었다), STOPPED 는 정지
						if(status==PlcStatus.RUNNING || status==PlcStatus.PAUSED) {
							vplcManipulationService.startVirtualPlc(vplcInfo.getId());
							if(status==PlcStatus.PAUSED) {
								vplcManipulationService.pauseVirtualPlc(vplcInfo.getId());
							}
						} else if(status==PlcStatus.STOPPED) {
							VirtualPlcRegistry.instance().get(vplcInfo.getId()).stop();
						}
					} catch (VirtualPlcException e) {
						log.warn("가상 PLC 를 되살리지 못했습니다: {} — {}", vplcInfo.getName(), e.getMessage());
					}
				}
			});
			this.readyClientConnectionEventHandler();
		} catch (VirtualPlcException e) {
			log.warn("Fail to load VirtualPlcs. System would be shutdown.", e);
			System.exit(0);
		}
	}
	
	
	private void readyClientConnectionEventHandler () {
		log.debug("");
		ClientConnectionEventDispatcher.getDispatcher().addEventListener(cceventDBHandler);
	}
}
