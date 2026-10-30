package com.wow.be.tc.vplc.lifecycle;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;
import com.wow.be.tc.vplc.manipulation.VirtualPlcManipulationService;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 종료 때 해제(RELEASED)·등록(REGISTERED) 상태가 아닌 가상 PLC 를 모두 멈춰 포트를 돌려준다.
 */
@Component
@Slf4j
public class VirtualPlcResourceReleaseShutdownHook {
	
	@Autowired
	private VirtualPlcManagementService virtualPlcManagementService;
	
	@Autowired
	private VirtualPlcManipulationService virtualPlcManupulationService;

	@PreDestroy
	public void releaseAllResource() {
		log.info("");
		try {
			List<VirtualPlcInfo> vplcList=virtualPlcManagementService.searchAllVirtualPlc();
			vplcList.forEach(vplc->{
				try {
					if(vplc.getStatus()!=PlcStatus.RELEASED && vplc.getStatus()!=PlcStatus.REGISTERED) {
						log.info("Releasing virtual plc network resourceL, Name: {}", vplc.getName());
						virtualPlcManupulationService.stopVirtualPlc(vplc.getId());	
					}
				} catch (VirtualPlcException e) {
					e.printStackTrace();
				}
			});
		} catch (VirtualPlcException e) {
			log.warn("", e);
		}
	}
}
