package com.wow.be.tc.plc.management;

import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.framework.Condition;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorInfoStatus;
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
				props.put("asyncUse", plcInfo.isAsyncRequestUse());
				props.put("messageFrameFormat", plcInfo.getMessageFrameFormat().getName());
				props.put("asyncTimerStartDelay", 1000L);
				props.put("asyncTimerRunInterval", 2000L);
				props.put("headerReqTemplate", plcInfo.getCommProtocolHeadersReqTemplate());
				props.put("headerResTemplate", plcInfo.getCommProtocolHeadersResTemplate());
				props.put("infoStatus", plcInfo.getStatus1().name());
				props.put("operStatus", plcInfo.getStatus2().name());
				props.put("plcRetryCount", plcConfig.getRetryCount());
				props.put("plcRetryIntervalRate", plcConfig.getRetryIntervalRate());
				props.put("plcRetryIntervalIncrement", plcConfig.getRetryIntervalIncrement());
				
				PlcConnectionEventListener connEventListener=new PlcConnectionEventDbHandler(plcManagementService);
				props.put("connectionListener", connEventListener);
				
				connectorManager.loadProgrammableControllerAdapter(props);
			}
		} catch (ToolControlException e) {
			log.warn("", e);
			// 시스템 시작 시에는 하나라도 load에 실패하면 시스템을 종료한다
			System.exit(0);
		}
	}
}
