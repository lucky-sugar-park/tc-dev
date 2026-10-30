package com.wow.be.tc.vplc.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.ClientConnectionEvent;
import com.wow.be.tc.vplc.machine.ClientConnectionEventListener;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ConnectionClientEventDbHandler implements ClientConnectionEventListener {
	
	@Autowired
	private VirtualPlcManagementService virtualPlcManagementService;

	@Override
	public String getName() {
		return "CONNECTION_CLIENT_EVENT_DB_HANDLER";
	}

	@Override
	public void onClientConnectionChanged(ClientConnectionEvent cce) {
		log.debug("");
		ConnectionClientInfo ccInfo=new ConnectionClientInfo();
		ccInfo.setConnOrDisconn(cce.getConnOrDisconn());
		ccInfo.setVplcId(cce.getVplcId());
		ccInfo.setPort(cce.getPort());
		ccInfo.setClientChannelId(cce.getChannelId());
		ccInfo.setClientIp(cce.getClientIpAddress());
		ccInfo.setConnTimestamp(cce.getTimestamp());
		
		ConnectionClientHistoryInfo cchInfo=new ConnectionClientHistoryInfo();
		cchInfo.setVplcId(cce.getVplcId());
		cchInfo.setPort(cce.getPort());
		cchInfo.setClientChannelId(cce.getChannelId());
		cchInfo.setClientIp(cce.getClientIpAddress());
		cchInfo.setTimestamp(cce.getTimestamp());
		cchInfo.setConnOrDisconn(cce.getConnOrDisconn());
		cchInfo.setDescription(cce.getDescription());
		
		ccInfo.addConnectionClientHistory(cchInfo);
		try {
			virtualPlcManagementService.saveConnectionClient(ccInfo);
		} catch (VirtualPlcException e) {
			log.warn("", e);
		}
	}
}
