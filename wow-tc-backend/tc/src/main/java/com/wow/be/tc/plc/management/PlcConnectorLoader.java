package com.wow.be.tc.plc.management;

import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.framework.Condition;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorInfoStatus;
import com.wow.be.tc.plc.connector.PlcAdapterManager;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PlcConnectorLoader {

	@Autowired
	private PlcConnectorManagementService plcManagementService;
	
	@Autowired
	private PlcConfig plcConfig;

	@Autowired
	private PlcConnectionStateStore stateStore;
	
	@Autowired
	private PlcOwnershipManager ownership;

	@PostConstruct
	public void init() {
		log.debug("Loading Programmable Controllers...");
		if(ownership.isEnabled()) {
			// 다중화 — 기동이 끝나면 소유권 관리자가 임대를 잡은 커넥터만 연결한다(docs/plc-ha-design.md §3.3)
			log.info("PLC 소유권 관리가 켜져 있어 기동 때 바로 연결하지 않습니다 — 임대를 얻은 커넥터부터 연결합니다.");
			return;
		}
		try {
			List<PlcConnectorInfo> plcInfos=plcManagementService.searchPlcConnectores(new Condition());
			PlcAdapterManager connectorManager=PlcAdapterManager.getPlcManager();
			for(PlcConnectorInfo plcInfo:plcInfos) {
				if(plcInfo.getStatus1()!=PlcConnectorInfoStatus.PUBLISHED) continue;

				// 기동 때는 PLC 가 아직 안 떠 있어도 어댑터를 올리고 뒤에서 연결한다 — 예전에는 재시도를 다 쓰면 아래에서 앱이 종료됐다
				Properties props=PlcAdapterProps.of(plcInfo, plcConfig, new PlcConnectionEventDbHandler(plcManagementService, stateStore), true);
				connectorManager.loadProgrammableControllerAdapter(props);
			}
		} catch (ToolControlException e) {
			log.warn("", e);
			// 시스템 시작 시에는 하나라도 load에 실패하면 시스템을 종료한다
			System.exit(0);
		}
	}

	// 앱 종료 때 PLC 연결을 닫는 일은 PlcShutdownLifecycle 이 한다 — 웹 서버가 진행 중 요청을 마친 뒤에 닫아야 해서
	// @PreDestroy 가 아니라 SmartLifecycle 단계로 순서를 정한다(docs/plc-ha-design.md §5.2).
}
