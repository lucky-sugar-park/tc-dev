package com.wow.be.tc.plc.management;

import com.wow.be.tc.Types.PlcConnectorInfoStatus;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;
import com.wow.be.tc.utils.TimerUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlcConnectionEventDbHandler implements PlcConnectionEventListener {
	
	private PlcConnectorManagementService plcConnectorManagementService;
	
	public PlcConnectionEventDbHandler(PlcConnectorManagementService plcConnectorManagementService) {
		this.plcConnectorManagementService=plcConnectorManagementService;
	}
	
	@Override
	public void onEvent(PlcConnectorEvent pevent) {
		log.debug("");
		PlcConnectorInfo pInfo=plcConnectorManagementService.findPlcConnectorById(pevent.getPlcConnectorId());
		
		// 우선 DB에 이벤트를 남김
		PlcConnectionEventHistInfo hInfo=new PlcConnectionEventHistInfo();
		hInfo.setPlcConnectorId(pevent.getPlcConnectorId());
		hInfo.setPlcConnectorName(pevent.getPlcConnectorName());
		hInfo.setEventType(pevent.getType().name());
		hInfo.setCause(pevent.getCause());
		hInfo.setTimestamp(pevent.getTimestamp());
		hInfo.setEventJson(pevent.json(true));
		hInfo.setDescription(pevent.getDescription());
		plcConnectorManagementService.savePlcConnectionEventHistory(hInfo);

		switch(pevent.getType()) {
		case DISCONNECT:
			if(pevent.getActiveChannelCount()<=0 && pevent.getOperStatus()==PlcConnectorOperStatus.RUNNING) {
				// 모든 채널이 disconnected 되었으므로 release 시킨 이후에, 다시 start 시킴
				plcConnectorManagementService.releasePlcConnector(pevent.getPlcConnectorId());
				hInfo.setCause("NO_AVAILABLE_CONNECTION_AND_RELEASE_COMMAND_BY_SYSTEM");
				hInfo.setStatus1(PlcConnectorInfoStatus.RELEASED);
				hInfo.setStatus2(PlcConnectorOperStatus.STOPPED);
				plcConnectorManagementService.savePlcConnectionEventHistory(hInfo);
				
				TimerUtil.waitFor(1500);

				plcConnectorManagementService.publishPlcConnector(pevent.getPlcConnectorId());
				hInfo.setCause("PUBLISH_COMMAND_BY_SYSTEM");
				hInfo.setStatus1(PlcConnectorInfoStatus.PUBLISHED);
				hInfo.setStatus2(PlcConnectorOperStatus.RUNNING);
				plcConnectorManagementService.savePlcConnectionEventHistory(hInfo);
			}
			break;
		case CONNECT:
			// no need to handle (this would be sent for each channel's connection)
			break;
		case START:
			pInfo.setStatus1(PlcConnectorInfoStatus.PUBLISHED);
			pInfo.setStatus2(PlcConnectorOperStatus.RUNNING);
			plcConnectorManagementService.updatePlcConnector(pInfo);
		
			hInfo.setCause("START_UPDATE");
			hInfo.setStatus1(PlcConnectorInfoStatus.PUBLISHED);
			hInfo.setStatus2(PlcConnectorOperStatus.RUNNING);
			plcConnectorManagementService.savePlcConnectionEventHistory(hInfo);
			break;
		case STOP:
		case SHUTDOWN:
			pInfo.setStatus1(PlcConnectorInfoStatus.RELEASED);
			pInfo.setStatus2(PlcConnectorOperStatus.STOPPED);
			plcConnectorManagementService.updatePlcConnector(pInfo);
			
			hInfo.setCause("SHUTDOWN_UPDATE");
			hInfo.setStatus1(PlcConnectorInfoStatus.RELEASED);
			hInfo.setStatus2(PlcConnectorOperStatus.STOPPED);
			plcConnectorManagementService.savePlcConnectionEventHistory(hInfo);
			break;
		default:
			break;
		}
	}
}
