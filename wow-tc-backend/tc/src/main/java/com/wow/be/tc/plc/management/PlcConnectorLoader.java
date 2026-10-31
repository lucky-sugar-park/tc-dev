package com.wow.be.tc.plc.management;

import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.framework.Condition;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorInfoStatus;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;

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
	
	@PostConstruct
	public void init() {
		log.debug("Loading Programmable Controllers...");
		try {
			List<PlcConnectorInfo> plcInfos=plcManagementService.searchPlcConnectores(new Condition());
			PlcAdapterManager connectorManager=PlcAdapterManager.getPlcManager();
			for(PlcConnectorInfo plcInfo:plcInfos) {
				if(plcInfo.getStatus1()!=PlcConnectorInfoStatus.PUBLISHED) continue;

				Properties props=new Properties();
				props.put("id", plcInfo.getId());
				props.put("name", plcInfo.getName());
				props.put("hostIp", plcInfo.getHostIp());
				props.put("className", plcInfo.getAdapterClassName());
				props.put("targetPorts", plcInfo.getPorts());
				props.put("localVips", plcInfo.getConnectionVips());
				props.put("messageFrameFormat", plcInfo.getMessageFrameFormat().getName());
				props.put("headerReqTemplate", plcInfo.getCommProtocolHeadersReqTemplate());
				props.put("headerResTemplate", plcInfo.getCommProtocolHeadersResTemplate());
				props.put("infoStatus", plcInfo.getStatus1().name());
				// 운영자 의도(STATUS1=PUBLISHED)만 본다 — STATUS2 는 마지막 제어 결과일 뿐 실제 연결 상태가 아니다(Redis, docs/plc-ha-design.md §3.4)
				props.put("operStatus", PlcConnectorOperStatus.RUNNING.name());
				props.put("plcRetryCount", plcConfig.getRetryCount());
				props.put("plcRetryIntervalRate", plcConfig.getRetryIntervalRate());
				props.put("plcRetryIntervalIncrement", plcConfig.getRetryIntervalIncrement());
				props.put("plcRequestTimeoutMs", plcConfig.getRequestTimeoutMs());
				// 기동 때는 PLC 가 아직 안 떠 있어도 어댑터를 올리고 뒤에서 연결한다 — 예전에는 재시도를 다 쓰면 아래에서 앱이 종료됐다
				props.put("startEvenIfDisconnected", Boolean.TRUE);
				
				PlcConnectionEventListener connEventListener=new PlcConnectionEventDbHandler(plcManagementService, stateStore);
				props.put("connectionListener", connEventListener);
				
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
