package com.wow.be.tc.vplc.machine;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.PushEventPayload;
import com.wow.be.tc.vplc.machine.VirtualProgrammableController.PortConnectionLimitEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientChannelStatusChangeEventHandler implements PropertyChangeListener {
	
	private static ClientConnectionEventDispatcher dispatcher;
	
	static {
		dispatcher=ClientConnectionEventDispatcher.getDispatcher();
	}
	
	private Map<Integer, Map<String, ClientChannel>> channelMap;
	private Map<Integer, Integer> limits;
	
	public ClientChannelStatusChangeEventHandler () {
		this.channelMap=new HashMap<>();
		this.limits=new HashMap<>();
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		log.debug("ClientChannelStatusChangeEventHandler.propertyChange(PropertyChangeEvent evt)...");
		
		if(evt instanceof ChannelEvent) {
			ClientChannel clientChannel=((ChannelEvent)evt).getClientChannel();
			int serverPort=clientChannel.getPort();
			Map<String, ClientChannel> portChannels=channelMap.get(serverPort);
			if(portChannels==null) {
				portChannels=new HashMap<>();
				channelMap.put(serverPort, portChannels);
			}
			
			boolean go=true;
			switch(evt.getPropertyName()) {
			case "ACTIVE":
				int limit=limits.get(serverPort);
				if(portChannels.size()>=limit) {
					log.debug("Client connection per port cannot exceed the limit: {}", limits.get(serverPort));
					clientChannel.destroy();
					go=false;
				} else {
					log.debug("");
					portChannels.put(clientChannel.getId(), clientChannel);
				}
				break;
			case "INACTIVE":
				log.debug("");
				portChannels.remove(clientChannel.getId());
				clientChannel.destroy();
				break;
			default:
				break;
			}
			channelMap.put(serverPort, portChannels);
			
			if(!go) return;

			// 연결된 클라아인트 커넥션 정보 DBMS 처리
			ClientConnectionEvent cce=new ClientConnectionEvent();
			cce.setVplcId(clientChannel.getVplcId());
			cce.setPort(serverPort);
			cce.setChannelId(clientChannel.getId());
			cce.setClientIpAddress(clientChannel.getRemoteIp());
			cce.setConnOrDisconn("ACTIVE".equals(evt.getPropertyName()) ? "CONNECTED" : "DISCONNECTED");
			cce.setTimestamp(new Date());
			cce.setDescription("Remote client is connected / disconnected to virtual plc with socket communication.");

			dispatcher.dispatch(cce);
			
			// monitoring 하는 클라이언트로 channel 변경내용을 push 한다
			EventPusher.newPusher().push(new PushEventPayload() {
				@Override
				public String toJson() {
					return null;
				}
			});			
		} else if(evt instanceof PortConnectionLimitEvent) {
			PortConnectionLimitEvent event=(PortConnectionLimitEvent)evt;
			int startPort=event.getStartPort();
			int portCount=event.getPortCount();
			int portConnLimit=event.getPortConnLimit();

			for(int i=startPort;i<startPort+portCount;i++) {
				this.limits.put(i, portConnLimit);
			}
		}
	}
	
	public Map<Integer, Map<String, ClientChannel>> getConnectedChannelMap() {
		return this.channelMap;
	}
}

